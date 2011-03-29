package com.google.gerrit.httpd.rpc.project.constraints;

class InvalidRangeException extends Exception {

  private static final long serialVersionUID = 1L;
  private short min;
  private short max;

  InvalidRangeException(short min, short max) {
    super("Invalid range [min,max] = [" + min + "," + max + "]");
    this.min = min;
    this.max = max;
  }

  public short getMin() {
    return min;
  }

  public short getMax() {
    return max;
  }
}
