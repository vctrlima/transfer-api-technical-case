package com.personal.transfer.domain.valueobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SPEC-UT-01: Validação de Valor da Transferência (RN-01)")
class MoneyTest {

    @Test
    @DisplayName("amount = 0 → lançar IllegalArgumentException")
    void givenZeroAmount_whenCreating_thenThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("amount = -50 → lançar IllegalArgumentException")
    void givenNegativeAmount_whenCreating_thenThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-50")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("amount = 0.001 → lançar IllegalArgumentException (mais de 2 casas decimais)")
    void givenAmountWithMoreThanTwoDecimals_whenCreating_thenThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> new Money(new BigDecimal("0.001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 decimal");
    }

    @Test
    @DisplayName("amount = 100.00 → instanciar com sucesso")
    void givenValidAmount_whenCreating_thenSucceeds() {
        Money money = new Money(new BigDecimal("100.00"));
        assertThat(money.value()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("amount = 999.99 → instanciar com sucesso")
    void givenMaxValidAmount_whenCreating_thenSucceeds() {
        Money money = new Money(new BigDecimal("999.99"));
        assertThat(money.value()).isEqualByComparingTo("999.99");
    }

    @Test
    @DisplayName("amount = null → lançar IllegalArgumentException")
    void givenNullAmount_whenCreating_thenThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> new Money(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
