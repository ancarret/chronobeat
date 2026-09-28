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
});
