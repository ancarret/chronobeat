import { TestBed } from '@angular/core/testing';
import { OnlineSession } from '../models/room.model';
import { OnlineSessionService } from './online-session.service';

const KEY = 'chronobeat.online';
const seat = (n: number): OnlineSession => ({ gameId: `game-${n}`, roomCode: 'BCDF', playerId: `p${n}`, playerToken: `t${n}` });

describe('OnlineSessionService', () => {
  /** A fresh service that reads whatever is in storage, like a page load. */
  function pageLoad(stored?: unknown): OnlineSessionService {
    localStorage.clear();
    if (stored !== undefined) {
      localStorage.setItem(KEY, typeof stored === 'string' ? stored : JSON.stringify(stored));
    }
    TestBed.resetTestingModule();
    return TestBed.inject(OnlineSessionService);
  }

  afterEach(() => localStorage.clear());

  it('remembers a seat and hands it back for that game only', () => {
    const service = pageLoad();
    service.remember(seat(1));

    expect(service.forGame('game-1')?.playerToken).toBe('t1');
    expect(service.forGame('game-2')).toBeNull();
  });

  it('survives a reload by reading its storage back', () => {
    pageLoad().remember(seat(1));
    const stored = localStorage.getItem(KEY);

    TestBed.resetTestingModule();
    localStorage.setItem(KEY, stored!);
    expect(TestBed.inject(OnlineSessionService).forGame('game-1')?.playerId).toBe('p1');
  });

  it('forgets a seat', () => {
    const service = pageLoad();
    service.remember(seat(1));
    service.forget('game-1');

    expect(service.forGame('game-1')).toBeNull();
    expect(JSON.parse(localStorage.getItem(KEY)!)).toEqual([]);
  });

  it('keeps only the most recent seats so storage cannot grow forever', () => {
    const service = pageLoad();
    for (let i = 1; i <= 15; i++) service.remember(seat(i));

    expect(service.forGame('game-1')).toBeNull();
    expect(service.forGame('game-15')).not.toBeNull();
    expect(JSON.parse(localStorage.getItem(KEY)!)).toHaveLength(10);
  });

  it('ignores corrupt storage instead of crashing', () => {
    expect(pageLoad('{not json').forGame('game-1')).toBeNull();
    expect(pageLoad({ not: 'a list' }).forGame('game-1')).toBeNull();
    expect(pageLoad([{ gameId: 'game-1' }]).forGame('game-1')).toBeNull(); // no token: unusable
  });

  it('still works in memory when storage refuses writes', () => {
    const service = pageLoad();
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota');
    });

    service.remember(seat(1));

    expect(service.forGame('game-1')?.playerToken).toBe('t1');
    spy.mockRestore();
  });
});
