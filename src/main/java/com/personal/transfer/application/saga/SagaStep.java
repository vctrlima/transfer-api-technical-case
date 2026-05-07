package com.personal.transfer.application.saga;

public interface SagaStep<C> {

    void execute(C context);

    void compensate(C context);
}
