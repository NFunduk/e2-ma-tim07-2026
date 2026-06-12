package com.example.sabona.model;

import com.google.firebase.firestore.Exclude;

import java.util.List;

public class AssociationGame {

    @Exclude
    public String docId;
    public String finalAnswer;
    public List<AssociationColumn> columns;

    public int order;

    public AssociationGame() {}
}