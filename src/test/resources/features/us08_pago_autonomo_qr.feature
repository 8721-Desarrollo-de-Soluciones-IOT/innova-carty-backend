Feature: Liquidación de orden y pago autónomo con QR dinámico (US08)
  Como comprador moderno
  Quiero pagar mi compra desde la app escaneando el QR del carrito
  Para salir de la tienda sin pasar por caja

  Background:
    Given un carrito inteligente nuevo conectado a la tienda
    And un comprador registrado en la app
    And el carrito reporta los siguientes tags RFID:
      | tag            | cantidad |
      | 04414793C92A81 | 1        |
      | 044D4593C92A81 | 2        |
    And el comprador vincula el carrito escaneando su QR

  Scenario: Pago aprobado con Yape
    When el comprador paga con "YAPE"
    Then la respuesta tiene el código 201
    And el pago queda aprobado por S/ 9.80 con un código de operación de 8 dígitos
    And el carrito queda en estado "PAID"

  Scenario: El pago se pausa si hay una discrepancia de peso
    Given la celda de carga reporta un peso estable de 1700 g
    When el comprador paga con "PLIN"
    Then la respuesta tiene el código 409

  Scenario: Una orden pagada no se cobra dos veces
    Given el comprador paga con "YAPE"
    When el comprador paga con "YAPE"
    Then la respuesta tiene el código 409
