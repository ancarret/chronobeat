import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { GameState } from '../../core/models/game.model';
import { OnlineSessionService } from '../../core/services/online-session.service';
import { game, player, result, round, settings, state, summary } from '../../testing/fixtures';
import { Game } from './game';

const API = 'http://localhost:8080/api/games/g1';

describe('Game screen', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  function setup(seat?: { playerId: string; playerToken: string }) {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [Game],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ gameId: 'g1' }) } } },
      ],
    });
    if (seat) {
      TestBed.inject(OnlineSessionService).remember({ gameId: 'g1', roomCode: 'BCDF', ...seat });
    }
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Game);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  const stateRequest = () => http.expectOne((r) => r.method === 'GET' && r.url === `${API}/state`);

  /** Serve the game's opening state request and render the result. */
  function open(fixture: ReturnType<typeof setup>, s: GameState): TestRequest {
    const req = stateRequest();
    req.flush(s);
    fixture.detectChanges();
    return req;
  }

  const el = (fixture: ReturnType<typeof setup>) => fixture.nativeElement as HTMLElement;
  const text = (fixture: ReturnType<typeof setup>) => el(fixture).textContent ?? '';

  it('shows a spinner until the first state arrives', () => {
    const fixture = setup();
    expect(el(fixture).querySelector('app-loading-state')).not.toBeNull();
    stateRequest().flush(state());
  });

  it('lets a solo player answer straight away', () => {
    const fixture = setup();
    open(fixture, state());

    expect(el(fixture).querySelector('.mystery-card')).not.toBeNull();
    expect(el(fixture).querySelector('.interstitial')).toBeNull();
    expect(el(fixture).querySelector('app-player-strip')).toBeNull(); // nobody to race against
  });

  it('asks a shared device to be handed over before showing each player their round', () => {
    const fixture = setup();
    const bea = player({ id: 'b', displayName: 'Bea' });
    open(fixture, state({
      game: game({ mode: 'LOCAL_MULTIPLAYER', players: [player(), bea] }),
      viewerPlayerId: 'b',
      round: round({ roundId: 'rb', playerId: 'b', playerDisplayName: 'Bea' }),
    }));

    expect(text(fixture)).toContain("Bea, you're up");
    expect(el(fixture).querySelector('.mystery-card')).toBeNull(); // no peeking at the next player's timeline

    (el(fixture).querySelector('.interstitial .btn-primary') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el(fixture).querySelector('.interstitial')).toBeNull();
    expect(el(fixture).querySelector('.mystery-card')).not.toBeNull();
  });

  it('does not hand the device over in an online game, where everyone has their own screen', () => {
    const fixture = setup({ playerId: 'p1', playerToken: 'secret' });
    const req = open(fixture, state({
      game: game({ mode: 'ONLINE_MULTIPLAYER', players: [player(), player({ id: 'b', displayName: 'Bea' })] }),
    }));

    expect(req.request.params.get('playerId')).toBe('p1'); // asks for its own seat's view
    expect(el(fixture).querySelector('.interstitial')).toBeNull();
    expect(el(fixture).querySelector('.mystery-card')).not.toBeNull();
    expect(el(fixture).querySelector('app-player-strip')).not.toBeNull();
  });

  it('asks the server who is up when nobody chose a viewer (shared device)', () => {
    const fixture = setup();
    const req = open(fixture, state());
    expect(req.request.params.has('playerId')).toBe(false);
  });

  it('shows the round clock when the game is timed', () => {
    const fixture = setup();
    open(fixture, state({
      game: game({ settings: settings({ answerSeconds: 30 }) }),
      round: round({ answerSecondsRemaining: 20 }),
      answerSecondsRemaining: 20,
    }));

    const clock = el(fixture).querySelector('.clock') as HTMLElement;
    expect(clock).not.toBeNull();
    expect(clock.textContent).toContain('20s');
    expect((clock.querySelector('.clock-fill') as HTMLElement).style.width).toContain('66');
  });

  it('locks in the chosen slot and then re-reads the state', () => {
    const fixture = setup();
    open(fixture, state());

    (el(fixture).querySelectorAll('.slot')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    (el(fixture).querySelector('.submit-btn') as HTMLButtonElement).click();

    const submit = http.expectOne(`${API}/rounds/r1/answer`);
    expect(submit.request.body).toEqual({ insertPosition: 1, guessedSongId: null, guessedYear: null });
    submit.flush({ resolved: false, waitingFor: 1, result: null });

    // Whatever the outcome, the server's state decides what happens next.
    stateRequest().flush(
      state({
        game: game({ settings: settings({ playStyle: 'SHARED_SONGS' }), players: [player({ answered: true }), player({ id: 'b', displayName: 'Bea' })] }),
        phase: 'WAITING',
        round: null,
        waitingOnPlayerIds: ['b'],
      }),
    );
    fixture.detectChanges();

    expect(text(fixture)).toContain('Locked in');
    expect(text(fixture)).toContain('Waiting for Bea');
  });

  it('sends no position for the free first card', () => {
    const fixture = setup();
    open(fixture, state({ round: round({ anchorRound: true, timeline: [] }) }));

    (el(fixture).querySelector('.submit-btn') as HTMLButtonElement).click();

    const submit = http.expectOne(`${API}/rounds/r1/answer`);
    expect(submit.request.body.insertPosition).toBeNull();
    submit.flush({ resolved: true, waitingFor: 0, result: result() });
    stateRequest().flush(state());
  });

  it('tells spectators who the table is waiting for', () => {
    const fixture = setup({ playerId: 'b', playerToken: 'secret' });
    const ana = player({ id: 'a', displayName: 'Ana' });
    open(fixture, state({
      game: game({ mode: 'ONLINE_MULTIPLAYER', settings: settings({ playStyle: 'TURN_BASED' }), players: [ana, player({ id: 'b', displayName: 'Bea' })] }),
      phase: 'WAITING',
      viewerPlayerId: 'b',
      round: null,
      waitingOnPlayerIds: ['a'],
    }));

    expect(text(fixture)).toContain('Ana is placing a card');
    // A spectator also gets to watch the active player's timeline.
    http.expectOne(`${API}/timeline?playerId=a`).flush([]);
  });

  it('names everyone still missing when several players are outstanding', () => {
    const fixture = setup({ playerId: 'a', playerToken: 'secret' });
    const players = [
      player({ id: 'a', displayName: 'Ana', answered: true }),
      player({ id: 'b', displayName: 'Bea' }),
      player({ id: 'c', displayName: 'Cy' }),
    ];
    open(fixture, state({
      game: game({ mode: 'ONLINE_MULTIPLAYER', settings: settings({ playStyle: 'SHARED_SONGS' }), players }),
      phase: 'WAITING',
      viewerPlayerId: 'a',
      round: null,
      waitingOnPlayerIds: ['b', 'c'],
    }));

    expect(text(fixture)).toContain('Waiting for Bea and Cy');
  });

  it('reveals the round and moves on with the round number it just watched', () => {
    const fixture = setup();
    open(fixture, state({ phase: 'REVEAL', round: null, summary: summary([result()], 4) }));

    expect(el(fixture).querySelector('app-round-result')).not.toBeNull();

    (el(fixture).querySelector('.continue-btn') as HTMLButtonElement).click();
    const next = http.expectOne((r) => r.method === 'POST' && r.url === `${API}/next-round`);
    expect(next.request.params.get('after')).toBe('4');
    next.flush(state({ round: round({ roundId: 'r5', roundNumber: 5 }), game: game({ currentRoundNumber: 5 }) }));
    fixture.detectChanges();

    expect(el(fixture).querySelector('app-round-result')).toBeNull();
    expect(el(fixture).querySelector('.mystery-card')).not.toBeNull();
  });

  it('looks again instead of failing when moving on is refused', () => {
    const fixture = setup();
    open(fixture, state({ phase: 'REVEAL', round: null, summary: summary() }));

    (el(fixture).querySelector('.continue-btn') as HTMLButtonElement).click();
    http.expectOne((r) => r.url === `${API}/next-round`).flush({ message: 'still on screen' }, { status: 409, statusText: 'Conflict' });

    stateRequest().flush(state({ phase: 'REVEAL', round: null, summary: summary() }));
    fixture.detectChanges();
    expect(el(fixture).querySelector('app-round-result')).not.toBeNull();
  });

  it('goes to the results from the final reveal', () => {
    const fixture = setup();
    open(fixture, state({ phase: 'FINISHED', round: null, summary: summary([result({ gameStatus: 'FINISHED' })]) }));

    expect(text(fixture)).toContain('See results');
    (el(fixture).querySelector('.continue-btn') as HTMLButtonElement).click();

    expect(navigate).toHaveBeenCalledWith(['/game', 'g1', 'results']);
  });

  it('sends a finished game with nothing left to reveal straight to the results', () => {
    const fixture = setup();
    open(fixture, state({ phase: 'FINISHED', round: null, summary: null }));

    expect(navigate).toHaveBeenCalledWith(['/game', 'g1', 'results'], { replaceUrl: true });
  });

  it('sends a game that has not started to its lobby', () => {
    const fixture = setup();
    open(fixture, state({ phase: 'LOBBY', round: null }));

    expect(navigate).toHaveBeenCalledWith(['/room', 'g1'], { replaceUrl: true });
  });

  it('shows the error, and tries again on request', () => {
    const fixture = setup();
    stateRequest().flush({ message: 'Game not found: g1', status: 404 }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(el(fixture).querySelector('app-error-state')).not.toBeNull();

    (el(fixture).querySelector('app-error-state button') as HTMLButtonElement).click();
    stateRequest().flush(state());
    fixture.detectChanges();
    expect(el(fixture).querySelector('.mystery-card')).not.toBeNull();
  });
});
