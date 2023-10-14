package de.theholyexception.livestreamirc.util.kaiutils;

public class ObjectStream<T> {
	private volatile ObjectStreamElement<T> last;
	private volatile ObjectStreamElement<T> first;
	/**
	 * ObjectStream is an ordinary simple Chained object-list.
	 * It works list an queue: reading the oldest entrys and writeng entry to the end of the list.
	 * 
	 * Note: Thread-save
	 */
	public ObjectStream(){}
	/**
	 * appends a Value to the Chain.
	 * @param val Value.
	 */
	public void put(T val){
		//System.out.println("ObjectStream::put " + val);
		ObjectStreamElement<T> e = new ObjectStreamElement<T>(val);
		synchronized(this){ // last muss wegen getNext synchron bleiben.
			if(last == null) {
				last = e;
			} else {
				if(last.getNext() == null){
					last.setNext(e);
				} else {
					first.setNext(e);
				}
			}
			first = e;
		}
	}
	/**
	 * Check if there are more Elements.
	 * @return true if there is at least one Element
	 */
	public boolean hasNext(){
		return last != null;
	}

	/**
	 * gets the next Element from the Chain.
	 * @return Value or null if Chain is empty
	 */
	public T getNext(){
		if(last == null) return null;
		T val;
		synchronized(this){ //first muss wegen put() synchron sein
			val = last.get();
		//System.out.println("ObjectStream::getNext " + val);
			if(last == first && first.getNext() == null) first = null; 
			last = last.getNext();
		}
		return val;
	}
}
