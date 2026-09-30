import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { GameSetup } from './game-setup';

describe('GameSetup', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GameSetup],
      providers: [provideRouter([]), { provide: HttpClient, useValue: {} }],
    }).compileComponents();
  });

  function create(): GameSetup {
    return TestBed.createComponent(GameSetup).componentInstance;
  }

  it('solo mode can always be submitted, even with a blank name (falls back to "You")', () => {
    const setup = create();
    setup.setMode('SOLO');
    expect(setup.canSubmit).toBe(true);
  });

  it('multiplayer mode requires at least two non-blank player names', () => {
    const setup = create();
    setup.setMode('LOCAL_MULTIPLAYER');
    expect(setup.canSubmit).toBe(false);

    setup.updatePlayerName(0, 'Andres');
    expect(setup.canSubmit).toBe(false); // second player still blank

    setup.updatePlayerName(1, 'Almudena');
    expect(setup.canSubmit).toBe(true);
  });

  it('blank (whitespace-only) player names do not count as filled', () => {
    const setup = create();
    setup.setMode('LOCAL_MULTIPLAYER');
    setup.updatePlayerName(0, 'Andres');
    setup.updatePlayerName(1, '   ');
    expect(setup.canSubmit).toBe(false);
  });

  it('addPlayer/removePlayer respect the 2-8 player bounds', () => {
    const setup = create();
    setup.setMode('LOCAL_MULTIPLAYER');
    for (let i = 0; i < 10; i++) {
      setup.addPlayer();
    }
    expect((setup as any).playerNames().length).toBe(8);

    for (let i = 0; i < 10; i++) {
      setup.removePlayer(0);
    }
    expect((setup as any).playerNames().length).toBe(2);
  });

  describe('play styles and goals', () => {
    it('defaults to turn-based with no goal and no clock', () => {
      const setup = create() as any;
      expect(setup.playStyle()).toBe('TURN_BASED');
      expect(setup.goal()).toBeNull();
      expect(setup.answerSeconds()).toBeNull();
    });

    it('proposes a race to 10 when the table chooses shared songs', () => {
      const setup = create() as any;
      setup.setMode('LOCAL_MULTIPLAYER');
      setup.setPlayStyle('SHARED_SONGS');
      expect(setup.goal()).toBe(10);

      setup.setPlayStyle('TURN_BASED');
      expect(setup.goal()).toBeNull();
    });

    it('never overrides a goal the player picked themselves', () => {
      const setup = create() as any;
      setup.setMode('LOCAL_MULTIPLAYER');
      setup.setGoal(5);
      setup.setPlayStyle('SHARED_SONGS');
      expect(setup.goal()).toBe(5);

      setup.setGoal(null); // an explicit "no goal" is a choice too
      setup.setPlayStyle('TURN_BASED');
      setup.setPlayStyle('SHARED_SONGS');
      expect(setup.goal()).toBeNull();
    });

    it('sends solo players back to turn-based, since there is nobody to share songs with', () => {
      const setup = create() as any;
      setup.setMode('LOCAL_MULTIPLAYER');
      setup.setPlayStyle('SHARED_SONGS');
      setup.setMode('SOLO');
      expect(setup.playStyle()).toBe('TURN_BASED');
    });
  });

  describe('online rooms', () => {
    it('needs a name, since other people will see it in the lobby', () => {
      const setup = create();
      setup.setMode('ONLINE_MULTIPLAYER');
      expect(setup.canSubmit).toBe(false);

      setup.updatePlayerName(0, '  ');
      expect(setup.canSubmit).toBe(false);

      setup.updatePlayerName(0, 'Andres');
      expect(setup.canSubmit).toBe(true);
    });

    it('hosts alone: the other seats fill up as friends join', () => {
      const setup = create() as any;
      setup.setMode('LOCAL_MULTIPLAYER');
      expect(setup.playerNames().length).toBe(2);
      setup.setMode('ONLINE_MULTIPLAYER');
      expect(setup.playerNames().length).toBe(1);
    });

    it('always runs on a clock: 45 seconds unless changed, and no "no limit" option', () => {
      const setup = create() as any;
      setup.setMode('ONLINE_MULTIPLAYER');

      expect(setup.answerSeconds()).toBe(45);
      expect(setup.timeLimits()).not.toContain(null);

      setup.setTimeLimit(20);
      expect(setup.answerSeconds()).toBe(20);
    });

    it('drops the default clock again when leaving online mode', () => {
      const setup = create() as any;
      setup.setMode('ONLINE_MULTIPLAYER');
      setup.setMode('LOCAL_MULTIPLAYER');
      expect(setup.answerSeconds()).toBeNull();
      expect(setup.timeLimits()).toContain(null);
    });

    it('keeps a clock the player chose when switching between modes', () => {
      const setup = create() as any;
      setup.setMode('LOCAL_MULTIPLAYER');
      setup.setTimeLimit(30);
      setup.setMode('ONLINE_MULTIPLAYER');
      expect(setup.answerSeconds()).toBe(30);
    });
  });
});
