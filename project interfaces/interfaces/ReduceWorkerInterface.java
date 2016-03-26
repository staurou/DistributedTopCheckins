package interfaces;

public interface ReduceWorkerInterface extends WorkerInterface {
	
	public void waitForMasterAck();
	public java.util.Map<Integer,Object> reduce(int x, Object y);
	public void sendResults(java.util.Map<Integer,Object> map);
	
}