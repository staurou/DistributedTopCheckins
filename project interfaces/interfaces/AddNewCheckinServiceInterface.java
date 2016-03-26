package interfaces;

import java.net.Socket;
import ssn.CheckinRequest;

public interface AddNewCheckinServiceInterface {
	
	public void initialize();
	public void waitForNewCheckinsThread();
	public void insertCheckingToDatabase(CheckinRequest x);
	public void ackToClient(Socket io);
	
}