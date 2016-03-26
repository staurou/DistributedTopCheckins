package interfaces;

public interface AndroidClientInterface {
	
	public void distributeToMappers();
	public void waitForMappers();
	public void ackToReducers();
	public void collectDataFromReducers();

}