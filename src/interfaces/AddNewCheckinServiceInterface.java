package interfaces;

import java.net.Socket;
import ssn.Checkin;

public interface AddNewCheckinServiceInterface {
	
	public void initialize();
	public void waitForNewCheckinsThread();
	public void insertCheckingToDatabase(Checkin x);
	public void ackToClient(Socket io);
	
}