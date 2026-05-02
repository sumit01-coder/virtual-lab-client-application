package com.virtuallab.client.api.dto;

public class ApiEnvelope<T> {
    public boolean status;
    public String message;
    public T data;
}
