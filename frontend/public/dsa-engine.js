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
      queue: [],         // Normal FIFO Queue: [ {token, label, name, phone, age, gender, date, wait} ]
      emergencies: [],   // Emergency LIFO Stack: [ {token, label, name, phone} ] (top is index 0)
      normalCounter: 0,
      emergencyCounter: 0,
      avgTime: 10,
      emergencyTime: 20,
      dailyLimit: 20     // Max patients allowed per day
    };

    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        const parsed = JSON.parse(saved);
        if (!parsed.dailyLimit) parsed.dailyLimit = 20;
        return parsed;
      }
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

  // Count patients currently in queue for a given calendar date
  function countForDate(state, dateStr) {
    if (!dateStr) return 0;
    return state.queue.filter(p => p.date === dateStr).length;
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
        age: p.age || 0,
        gender: p.gender || 'Other',
        date: p.date || '',
        wait: computeWait(state, p.token)
      }));

      return {
        emergencyMode: state.emergencies.length > 0,
        avgTime: state.avgTime,
        emergencyTime: state.emergencyTime,
        dailyLimit: state.dailyLimit || 20,
        current: state.current,
        queue: calculatedQueue,
        emergencies: state.emergencies.map(e => ({
          token: e.token,
          label: e.label,
          name: e.name
        }))
      };
    },

    // 2. Book Appointment (Enqueue to Queue - FIFO) with daily capacity limit
    book(name, phone, age = 0, gender = 'Other', date = null) {
      const state = getState();
      state.normalCounter++;

      const today = new Date().toISOString().split('T')[0];
      let assignedDate = date || today;
      let rolledOver = false;
      const originalDate = assignedDate;
      const limit = state.dailyLimit || 20;

      // Check daily capacity limit: if full, auto roll over to next available date
      let checkDate = new Date(assignedDate);
      while (countForDate(state, checkDate.toISOString().split('T')[0]) >= limit) {
        checkDate.setDate(checkDate.getDate() + 1);
        rolledOver = true;
      }
      assignedDate = checkDate.toISOString().split('T')[0];

      const patient = {
        token: state.normalCounter,
        label: String(state.normalCounter),
        name: name.trim(),
        phone: (phone || '').trim(),
        age: parseInt(age) || 0,
        gender: gender || 'Other',
        date: assignedDate,
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
        age: patient.age,
        gender: patient.gender,
        date: patient.date,
        wait: wait,
        rolledOver: rolledOver,
        originalDate: originalDate
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

    // 4. End Emergency Mode / Clear Emergency Stack
    endEmergency() {
      const state = getState();
      state.emergencies = [];
      if (state.current && state.current.emergency) {
        state.current.status = 'DONE';
        state.current = null;
      }
      saveState(state);
      return {
        success: true,
        emergencyEnded: true,
        emergencyMode: false,
        message: 'Emergency mode ended. Regular queue resumed.'
      };
    },

    // 5. Doctor Calls Next (LIFO Stack first, then FIFO Queue)
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

    // 6. Cancel Appointment
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

    // 7. Track Wait Time
    getWait(token) {
      const state = getState();

      // Check if current
      if (state.current && !state.current.emergency && state.current.token === token) {
        return {
          found: true,
          label: state.current.label,
          name: state.current.name,
          age: state.current.age || 0,
          gender: state.current.gender || 'Other',
          date: state.current.date || '',
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
          age: p.age || 0,
          gender: p.gender || 'Other',
          date: p.date || '',
          position: index + 1,
          wait: wait,
          status: 'WAITING'
        };
      }

      return { found: false, message: 'Token not in waiting queue' };
    },

    // 8. Save Settings (avgTime, emergencyTime, dailyLimit)
    saveSettings(avgTime, emergencyTime, dailyLimit) {
      const state = getState();
      if (avgTime > 0) state.avgTime = avgTime;
      if (emergencyTime > 0) state.emergencyTime = emergencyTime;
      if (dailyLimit > 0) state.dailyLimit = dailyLimit;
      saveState(state);
      return {
        success: true,
        avgTime: state.avgTime,
        emergencyTime: state.emergencyTime,
        dailyLimit: state.dailyLimit
      };
    }
  };
})();
