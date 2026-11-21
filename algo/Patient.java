public class Patient {
    public int token;          // 1, 2, 3 ... (emergency gets its own counter)
    public String name;
    public String phone;
    public boolean emergency;
    public String status;      // WAITING, SERVING, DONE, CANCELLED

    public Patient(int token, String name, String phone, boolean emergency) {
        this.token = token;
        this.name = name;
        this.phone = phone;
        this.emergency = emergency;
        this.status = "WAITING";
    }

    // Label shown to the user: 5 for normal, E2 for emergency
    public String label() {
        return emergency ? "E" + token : String.valueOf(token);
    }

    public String toString() {
        return label() + " - " + name + " [" + status + "]";
    }
}
