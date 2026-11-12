Feature: Webhook de confirmación de pagos externos (TS03)
  Como plataforma Innova Carty
  Quiero recibir la confirmación que envía el proveedor de pagos (Yape, Plin o pasarela de tarjeta)
  Para marcar la orden como pagada aunque la app del comprador se cierre

  Background:
    Given un carrito inteligente nuevo conectado a la tienda
    And un comprador registrado en la app
    And el carrito reporta los siguientes tags RFID:
      | tag            | cantidad |
      | 04414793C92A81 | 1        |
      | 044D4593C92A81 | 2        |
    And el comprador vincula el carrito escaneando su QR

  Scenario: El proveedor confirma el pago y el carrito pasa a pagado
    When el proveedor confirma el pago "YAPE" del evento "evt-yape-001" por S/ 9.80
    Then la respuesta tiene el código 200
    And el webhook responde "PAYMENT_CREATED"
    And el carrito queda en estado "PAID"

  Scenario: Una notificación repetida no crea un segundo pago
    Given el proveedor confirma el pago "PLIN" del evento "evt-plin-001" por S/ 9.80
    When el proveedor confirma el pago "PLIN" del evento "evt-plin-001" por S/ 9.80
    Then el webhook responde "DUPLICATE"
    And el carrito tiene 1 pago registrado

  Scenario: Un pago rechazado no cambia el estado del carrito
    When el proveedor rechaza el pago "YAPE" del evento "evt-yape-002"
    Then el webhook responde "REJECTION_RECORDED"
    And el carrito queda en estado "IN_SESSION"

  Scenario: Un monto distinto al total de la orden no se acepta
    When el proveedor confirma el pago "YAPE" del evento "evt-yape-003" por S/ 5.00
    Then la respuesta tiene el código 409
    And el carrito queda en estado "IN_SESSION"

  Scenario: Una notificación sin el secreto correcto se rechaza
    When llega una notificación de pago con el secreto "secreto-falso"
    Then la respuesta tiene el código 401
