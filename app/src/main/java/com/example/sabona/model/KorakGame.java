package com.example.sabona.model;

import com.google.firebase.firestore.Exclude;

import java.util.List;

public class KorakGame {

    /** Firestore document ID — popunjava se lokalno, nije u bazi */
    @Exclude
    public String docId;

    /** Tačan odgovor */
    public String answer;

    /** Koraci (maks 7), od najtežeg do najlakšeg */
    public List<String> steps;

    /** Redosled za sortiranje pri učitavanju */
    public int order;

    public KorakGame() {}
}