import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { USE_MOCK, WS_URL } from '../api/config';
import { tokenStore } from '../api/tokenStore';
import { subscribeStream } from '../api/mock';

/**
 * Holds one STOMP connection for the whole app. The Spring backend consumes
 * Kafka topics and relays them to /topic/transactions.
 */
const StreamContext = createContext({ status: 'offline', events: [], paused: false, setPaused: () => {} });
const MAX_EVENTS = 200;

export function StreamProvider({ children }) {
  const [status, setStatus] = useState('connecting');
  const [events, setEvents] = useState([]);
  const [paused, setPaused] = useState(false);
  const pausedRef = useRef(paused);
  pausedRef.current = paused;

  useEffect(() => {
    const push = (evt) => {
      if (pausedRef.current) return;
      setEvents((list) => [evt, ...list].slice(0, MAX_EVENTS));
    };

    if (USE_MOCK) {
      setStatus('live');
      return subscribeStream(push);
    }

    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${tokenStore.access()}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setStatus('live');
        client.subscribe('/topic/transactions', (msg) => {
          try { push(JSON.parse(msg.body)); } catch { /* ignore malformed */ }
        });
      },
      onWebSocketClose: () => setStatus('reconnecting'),
      onStompError: () => setStatus('error'),
    });
    client.activate();
    return () => { client.deactivate(); };
  }, []);

  return (
    <StreamContext.Provider value={{ status, events, paused, setPaused }}>
      {children}
    </StreamContext.Provider>
  );
}

export const useStream = () => useContext(StreamContext);
