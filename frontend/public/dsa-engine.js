/**
 * CarePlus Clinic - Client-Side DSA Engine (Queue + Stack)
 * Enables 100% full interactive functionality when deployed as a static site (e.g. Netlify / GitHub Pages)
 * Automatically syncs state across browser tabs via localStorage.
 */

const DSAEngine = (() => {
  const STORAGE_KEY = 'careplus_clinic_dsa_state';

  // Load state from localStorage or initialize defaults
  function getState() {
    const defaultState = {
      current: null,
      queue: [],         // Normal FIFO Queue: [ {token, label, name, phone, wait} ]
      emergencies: [],   // Emergency LIFO Stack: [ {token, label, name, phone} ] (top is index 0)
      normalCounter: 0,
      emergencyCounter: 0,
      avgTime: 10,
      emergencyTime: 20
    };

    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.warn('Could not read DSA state from localStorage', e);
    }
    return defaultState;
  }

  function saveState(state) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
      // Notify other tabs
      window.dispatchEvent(new Event('dsa-state-updated'));
    } catch (e) {
      console.warn('Could not save DSA state to localStorage', e);
    }
  }

  // Calculate waiting time for a token in the queue
  function computeWait(state, token) {
    let minutes = 0;
    if (state.current) {
      minutes += state.current.emergency ? state.emergencyTime : state.avgTime;
    }
    // Emergencies in stack go before regular queue
    minutes += state.emergencies.length * state.emergencyTime;

    let position = 0;
    for (const p of state.queue) {
      if (p.token === token) {
        return minutes + position * state.avgTime;
      }
      position++;
    }
    return -1;
  }

  return {
    // 1. Get Status
    getStatus() {
      const state = getState();
      const calculatedQueue = state.queue.map(p => ({
        token: p.token,
        label: p.label,
        name: p.name,
        phone: p.phone,
        wait: computeWait(state, p.token)
      }));

      return {
        emergencyMode: state.emergencies.length > 0,
        avgTime: state.avgTime,
        emergencyTime: state.emergencyTime,
        current: state.current,
        queue: calculatedQueue,
        emergencies: state.emergencies.map(e => ({
          token: e.token,
          label: e.label,
          name: e.name
        }))
      };
    },

    // 2. Book Appointment (Enqueue to Queue - FIFO)
    book(name, phone) {
      const state = getState();
      state.normalCounter++;
      const patient = {
        token: state.normalCounter,
        label: String(state.normalCounter),
        name: name.trim(),
        phone: (phone || '').trim(),
        emergency: false,
        status: 'WAITING'
      };

      state.queue.push(patient); // Enqueue to rear (FIFO)
      saveState(state);

      const wait = computeWait(state, patient.token);
      return {
        label: patient.label,
        token: patient.token,
        name: patient.name,
        phone: patient.phone,
        wait: wait
      };
    },

    // 3. Add Emergency (Push to Stack - LIFO)
    addEmergency(name) {
      const state = getState();
      state.emergencyCounter++;
      const emergency = {
        token: state.emergencyCounter,
        label: 'E' + state.emergencyCounter,
        name: name.trim(),
        phone: '-',
        emergency: true,
        status: 'WAITING'
      };

      state.emergencies.unshift(emergency); // Push to top of stack (index 0 is top)
      saveState(state);

      return {
        success: true,
        label: emergency.label,
        token: emergency.token,
        name: emergency.name
      };
    },

    // 4. Doctor Calls Next (LIFO Stack first, then FIFO Queue)
    callNext() {
      const state = getState();

      if (state.current) {
        state.current.status = 'DONE';
        state.current = null;
      }

      if (state.emergencies.length > 0) {
        // Pop from top of emergency stack (LIFO)
        state.current = state.emergencies.shift();
      } else if (state.queue.length > 0) {
        // Poll from front of regular queue (FIFO)
        state.current = state.queue.shift();
      }

      if (state.current) {
        state.current.status = 'SERVING';
      }

      saveState(state);
      return {
        success: true,
        current: state.current
      };
    },

    // 5. Cancel Appointment
    cancel(token) {
      const state = getState();
      const index = state.queue.findIndex(p => p.token === token);
      if (index !== -1) {
        state.queue.splice(index, 1);
        saveState(state);
        return { success: true };
      }
      return { success: false, message: 'Patient not found in queue or already called' };
    },

    // 6. Track Wait Time
    getWait(token) {
      const state = getState();

      // Check if current
      if (state.current && !state.current.emergency && state.current.token === token) {
        return {
          found: true,
          label: state.current.label,
          name: state.current.name,
          position: 0,
          wait: 0,
          status: 'SERVING'
        };
      }

      const index = state.queue.findIndex(p => p.token === token);
      if (index !== -1) {
        const p = state.queue[index];
        const wait = computeWait(state, token);
        return {
          found: true,
          label: p.label,
          name: p.name,
          position: index + 1,
          wait: wait,
          status: 'WAITING'
        };
      }

      return { found: false, message: 'Token not in waiting queue' };
    },

    // 7. Save Settings
    saveSettings(avgTime, emergencyTime) {
      const state = getState();
      if (avgTime > 0) state.avgTime = avgTime;
      if (emergencyTime > 0) state.emergencyTime = emergencyTime;
      saveState(state);
      return {
        success: true,
        avgTime: state.avgTime,
        emergencyTime: state.emergencyTime
      };
    }
  };
})();
