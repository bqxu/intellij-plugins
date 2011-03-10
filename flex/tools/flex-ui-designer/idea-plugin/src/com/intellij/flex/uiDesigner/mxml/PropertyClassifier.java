package com.intellij.flex.uiDesigner.mxml;

public final class PropertyClassifier {
  final static int PROPERTY = 0;
  final static int STYLE = 1;

  final static int ID = 2;
  final static int OBJECT_REFERENCE = 3;

  final static int MX_CONTAINER_CHILDREN = 4;
  
  final static int DEFERRED_INSTANCE_FROM_BYTES = 5;
  final static int ARRAY_OF_DEFERRED_INSTANCE_FROM_BYTES = 6;
  final static int VECTOR_OF_DEFERRED_INSTANCE_FROM_BYTES = 7;

  final static int FIXED_ARRAY = 8;
  final static int FIXED_HETEROGENEOUS_ARRAY = 9;
  
//  public static final int DICTIONARY = 10;
  public static final int VECTOR = 10;
}