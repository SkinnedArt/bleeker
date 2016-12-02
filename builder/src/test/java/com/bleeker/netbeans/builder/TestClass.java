/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bleeker.netbeans.builder;

import java.util.List;

/**
 * Class for testing builder generation.
 *
 * @author Arthur Bleeker
 */
public class TestClass extends TestAbstractClass implements TestInterface {

    private final int number;
    private final String text;
    private final Object object;
    private final List<Object> list;

    public TestClass(long uuid, int number, String text, Object object, List<Object> list) {
        super(uuid);
        this.number = number;
        this.text = text;
        this.object = object;
        this.list = list;
    }

    @Override
    public int getNumber() {
        return number;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public Object getObject() {
        return object;
    }

    @Override
    public List<Object> getList() {
        return list;
    }

}
