package de.theholyexception.livestreamirc.util.kaiutils;

public class ObjectStreamElement<T> {
	private T value;
	private ObjectStreamElement<T> next;
	public ObjectStreamElement(T val){
		value = val;
	}
	public T get(){
		return value;
	}
	
	public ObjectStreamElement<T> getNext(){
		return next;
	}
	
	public void setNext(ObjectStreamElement<T> next){
		this.next = next;
	}
}
