package com.tregy.backend.service;
public interface EmailService{void sendVerification(String recipient,String link);void sendPasswordReset(String recipient,String link);}
