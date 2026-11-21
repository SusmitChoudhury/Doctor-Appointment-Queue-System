public class Main {
    public static void main(String[] args) {
        AppointmentSystem s = new AppointmentSystem();
        s.setAvgTime(10);
        s.setEmergencyTime(20);

        s.bookAppointment("Amit", "9000000001");
        s.bookAppointment("Riya", "9000000002");
        s.bookAppointment("Karan", "9000000003");
        System.out.println("Karan wait: " + s.getEstimatedWait(3) + " min");   // 20

        System.out.println("Now: " + s.callNext());                            // 1
        s.addEmergency("Emergency A");
        s.addEmergency("Emergency B");
        System.out.println("Emergency mode: " + s.isEmergencyMode());
        System.out.println("Karan wait: " + s.getEstimatedWait(3) + " min");   // 10+40+10=60

        System.out.println("Now: " + s.callNext());                            // E2 (LIFO)
        System.out.println("Now: " + s.callNext());                            // E1
        System.out.println("Emergency mode: " + s.isEmergencyMode());          // false
        System.out.println("Cancel 3: " + s.cancelAppointment(3));
        System.out.println("Now: " + s.callNext());                            // 2
        System.out.println("Now: " + s.callNext());                            // null
    }
}
