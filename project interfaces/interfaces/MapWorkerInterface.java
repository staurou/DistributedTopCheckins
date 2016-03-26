package interfaces;

public interface MapWorkerInterface extends WorkerInterface {
	
	public java.util.Map<Integer,Object> map(Object key, Object data);
	public void notifyMaster();
	public void sendToReduce(java.util.Map<Integer,Object> topResults);

}