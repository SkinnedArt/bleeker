/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bleeker.netbeans.builder;

/**
 * Abstract class for testing builder generation.
 *
 * @author Arthur Bleeker
 */
public abstract class TestAbstractClass implements TestSuperInterface {

    private final long uuid;

    public TestAbstractClass(long uuid) {
        this.uuid = uuid;
    }

    @Override
    public long getUuid() {
        return uuid;
    }

}
