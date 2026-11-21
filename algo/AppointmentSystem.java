import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;
import java.util.ArrayList;

/*
 * Doctor Appointment System
 * Queue  -> normal patients (First In, First Out)
 * Stack  -> emergency patients (Last In, First Out)
 *
 * Rule: if the stack has anyone, the stack is served first.
 * When the stack becomes empty, the queue continues normally.
 */
public class AppointmentSystem {

    private Queue<Patient> queue = new LinkedList<>();   // normal patients
    private Stack<Patient> stack = new Stack<>();        // emergency patients
    private Patient current = null;                      // patient inside doctor's room

    private int normalCounter = 0;
    private int emergencyCounter = 0;

    private int avgTime = 10;          // minutes per normal patient (admin sets)
    private int emergencyTime = 20;    // minutes added per emergency (admin sets)

    // ---------- Admin settings ----------
    public void setAvgTime(int minutes) {
        if (minutes > 0) avgTime = minutes;
    }

    public void setEmergencyTime(int minutes) {
        if (minutes > 0) emergencyTime = minutes;
    }

    public int getAvgTime() { return avgTime; }
    public int getEmergencyTime() { return emergencyTime; }

    // ---------- 1. Book appointment (enqueue) ----------
    public Patient bookAppointment(String name, String phone) {
        Patient p = new Patient(++normalCounter, name, phone, false);
        queue.add(p);
        return p;
    }

    // ---------- 4. Emergency (push on stack) ----------
    public Patient addEmergency(String name) {
        Patient p = new Patient(++emergencyCounter, name, "-", true);
        stack.push(p);
        return p;
    }

    public boolean isEmergencyMode() {
        return !stack.isEmpty();
    }

    // ---------- Doctor calls the next patient ----------
    public Patient callNext() {
        if (current != null) {
            current.status = "DONE";
            current = null;
        }
        if (!stack.isEmpty()) {
            current = stack.pop();       // emergency first (LIFO)
        } else if (!queue.isEmpty()) {
            current = queue.poll();      // then normal (FIFO)
        }
        if (current != null) current.status = "SERVING";
        return current;
    }

    // ---------- 2. Who is running now ----------
    public Patient getCurrent() {
        return current;
    }

    // ---------- 6. Cancel booking ----------
    public boolean cancelAppointment(int token) {
        for (Patient p : queue) {
            if (p.token == token) {
                p.status = "CANCELLED";
                queue.remove(p);
                return true;
            }
        }
        return false;   // not found, already served, or in progress
    }

    // ---------- 3 & 5. Estimated waiting time ----------
    // Returns minutes, or -1 if the token is not waiting.
    public int getEstimatedWait(int token) {
        int minutes = 0;
        if (current != null) minutes += current.emergency ? emergencyTime : avgTime;

        // everyone in the emergency stack goes before the queue (time added here)
        minutes += stack.size() * emergencyTime;

        int position = 0;
        for (Patient p : queue) {
            if (p.token == token) return minutes + position * avgTime;
            position++;
        }
        return -1;
    }

    // ---------- Views for the frontend ----------
    public List<Patient> getQueueList() {
        return new ArrayList<>(queue);
    }

    public List<Patient> getEmergencyList() {
        List<Patient> list = new ArrayList<>(stack);
        java.util.Collections.reverse(list);   // top of stack first
        return list;
    }
}
