public class Patient {
    public int token;          // 1, 2, 3 ... (emergency gets its own counter)
    public String name;
    public String phone;
    public int age;
    public String gender;
    public String date;        // Appointment date: YYYY-MM-DD
    public boolean emergency;
    public String status;      // WAITING, SERVING, DONE, CANCELLED

    public Patient(int token, String name, String phone, boolean emergency) {
        this.token = token;
        this.name = name;
        this.phone = phone;
        this.age = 0;
        this.gender = "Other";
        this.date = "";
        this.emergency = emergency;
        this.status = "WAITING";
    }

    public Patient(int token, String name, String phone, int age, String gender, String date, boolean emergency) {
        this.token = token;
        this.name = name;
        this.phone = phone;
        this.age = age;
        this.gender = (gender != null && !gender.isEmpty()) ? gender : "Other";
        this.date = (date != null) ? date : "";
        this.emergency = emergency;
        this.status = "WAITING";
    }

    // Label shown to the user: 5 for normal, E2 for emergency
    public String label() {
        return emergency ? "E" + token : String.valueOf(token);
    }

    public String toString() {
        String details = (age > 0 ? " (" + age + ", " + gender + ")" : "");
        String dateStr = (!date.isEmpty() ? " on " + date : "");
        return label() + " - " + name + details + dateStr + " [" + status + "]";
    }
}
