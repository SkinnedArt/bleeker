/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.bleeker.netbeans.builder;

import java.util.List;

/**
 * Interface for testing builder generation.
 *
 * @author Arthur Bleeker
 */
public interface TestInterface extends TestSuperInterface {

    List<Object> getList();

    int getNumber();

    Object getObject();

    String getText();

}
