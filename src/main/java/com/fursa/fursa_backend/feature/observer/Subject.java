package com.fursa.fursa_backend.feature.observer;

public interface Subject {

    void attach(Observer observer);
    void detach(Observer observer);
    void notifyObservers(String event, Object data);
}
