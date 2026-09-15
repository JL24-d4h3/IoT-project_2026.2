# Especificación de Requisitos del Sistema

**Proyecto:** Sistema para la gestión de reservas de alojamiento en
hoteles vía aplicación móvil\
**Curso:** 1TEL05 -- Servicios y Aplicaciones para IoT\
**Versión:** 1.0\
**Fecha:** 30 de agosto de 2026

------------------------------------------------------------------------

## 1. Requisitos funcionales

Los requisitos funcionales especifican **qué debe hacer el sistema**.
Cada requisito es verificable mediante una operación, resultado
observable o condición de funcionamiento.

### 1.1 Gestión de usuarios y acceso

  -----------------------------------------------------------------------
  ID                      Requisito funcional     Prioridad
  ----------------------- ----------------------- -----------------------
  RF-001                  El sistema debe         Alta
                          permitir el registro de 
                          clientes mediante la    
                          aplicación móvil.       

  RF-002                  El sistema debe         Alta
                          habilitar               
                          automáticamente a un    
                          cliente después de      
                          completar correctamente 
                          su registro.            

  RF-003                  El sistema debe         Alta
                          permitir la             
                          autenticación de        
                          usuarios registrados.   

  RF-004                  El sistema debe         Alta
                          identificar el rol del  
                          usuario autenticado y   
                          proporcionar únicamente 
                          las funcionalidades     
                          correspondientes a      
                          dicho rol.              

  RF-005                  El sistema debe         Alta
                          permitir al Superadmin  
                          consultar los usuarios  
                          registrados.            

  RF-006                  El sistema debe         Alta
                          permitir al Superadmin  
                          activar o desactivar    
                          cuentas de              
                          administradores de      
                          hotel, taxistas y       
                          clientes.               

  RF-007                  El sistema debe         Alta
                          permitir al Superadmin  
                          registrar hoteles.      

  RF-008                  El sistema debe         Alta
                          permitir al Superadmin  
                          asignar un              
                          administrador a cada    
                          hotel registrado.       

  RF-009                  El sistema debe impedir Alta
                          que un usuario          
                          deshabilitado acceda a  
                          las funcionalidades     
                          protegidas del sistema. 
  -----------------------------------------------------------------------

### 1.2 Gestión de hoteles

  -----------------------------------------------------------------------
  ID                      Requisito funcional     Prioridad
  ----------------------- ----------------------- -----------------------
  RF-010                  El sistema debe         Alta
                          permitir al             
                          administrador de hotel  
                          registrar y actualizar  
                          la información de       
                          ubicación del hotel.    

  RF-011                  El sistema debe         Media
                          permitir al             
                          administrador de hotel  
                          registrar lugares       
                          históricos cercanos al  
                          hotel.                  

  RF-012                  El sistema debe         Alta
                          permitir al             
                          administrador de hotel  
                          registrar fotografías   
                          del hotel y sus         
                          instalaciones.          

  RF-013                  El sistema debe exigir  Alta
                          un mínimo de cuatro     
                          fotografías del hotel   
                          y/o sus instalaciones.  

  RF-014                  El sistema debe         Alta
                          permitir al             
                          administrador de hotel  
                          registrar habitaciones  
                          pertenecientes a su     
                          hotel.                  

  RF-015                  El sistema debe         Alta
                          permitir registrar el   
                          tipo de habitación.     

  RF-016                  El sistema debe         Alta
                          permitir registrar la   
                          capacidad máxima de     
                          adultos de una          
                          habitación.             

  RF-017                  El sistema debe         Alta
                          permitir registrar la   
                          capacidad máxima de     
                          niños de una            
                          habitación.             

  RF-018                  El sistema debe         Alta
                          permitir registrar el   
                          área de una habitación  
                          en metros cuadrados.    

  RF-019                  El sistema debe         Alta
                          permitir al             
                          administrador de hotel  
                          registrar los servicios 
                          ofrecidos por el hotel. 

  RF-020                  Cada servicio debe      Alta
                          registrar nombre,       
                          descripción y precio.   

  RF-021                  El sistema debe         Alta
                          permitir indicar si un  
                          servicio tiene costo.   

  RF-022                  El sistema debe         Media
                          permitir asociar        
                          imágenes a los          
                          servicios ofrecidos.    

  RF-023                  El administrador de     Alta
                          hotel solo debe poder   
                          gestionar información   
                          correspondiente a su    
                          propio hotel.           
  -----------------------------------------------------------------------

### 1.3 Consulta de hoteles y disponibilidad

  -----------------------------------------------------------------------
  ID                      Requisito funcional     Prioridad
  ----------------------- ----------------------- -----------------------
  RF-024                  El sistema debe         Alta
                          permitir al cliente     
                          consultar los hoteles   
                          disponibles.            

  RF-025                  El sistema debe         Alta
                          permitir al cliente     
                          consultar la            
                          información general de  
                          un hotel.               

  RF-026                  El sistema debe         Alta
                          permitir al cliente     
                          consultar las           
                          habitaciones de un      
                          hotel.                  

  RF-027                  El sistema debe         Alta
                          permitir al cliente     
                          consultar los servicios 
                          ofrecidos por un hotel. 

  RF-028                  El sistema debe         Media
                          permitir al cliente     
                          consultar las           
                          fotografías del hotel y 
                          sus instalaciones.      

  RF-029                  El sistema debe         Alta
                          permitir al cliente     
                          consultar las           
                          valoraciones de un      
                          hotel.                  

  RF-030                  El sistema debe         Alta
                          permitir consultar la   
                          disponibilidad de una   
                          habitación para un      
                          período determinado.    

  RF-031                  El sistema debe         Alta
                          considerar las fechas   
                          de entrada y salida al  
                          determinar la           
                          disponibilidad.         

  RF-032                  El sistema debe impedir Crítica
                          que una habitación sea  
                          reservada por más de un 
                          cliente cuando sus      
                          períodos de alojamiento 
                          se superpongan.         

  RF-033                  El sistema debe         Alta
                          actualizar la           
                          disponibilidad de una   
                          habitación cuando una   
                          reserva sea creada,     
                          modificada o finalizada 
                          según corresponda.      
  -----------------------------------------------------------------------

### 1.4 Gestión de reservas

  -----------------------------------------------------------------------
  ID                      Requisito funcional     Prioridad
  ----------------------- ----------------------- -----------------------
  RF-034                  El sistema debe         Crítica
                          permitir al cliente     
                          realizar reservas de    
                          alojamiento en hoteles. 

  RF-035                  El cliente debe poder   Alta
                          realizar reservas en    
                          diferentes hoteles.     

  RF-036                  El sistema debe validar Crítica
                          la disponibilidad de la 
                          habitación antes de     
                          confirmar una reserva.  

  RF-037                  El sistema debe         Crítica
                          rechazar una reserva si 
                          la habitación ya se     
                          encuentra ocupada       
                          durante el período      
                          solicitado.             

  RF-038                  El sistema debe         Alta
                          solicitar al cliente    
                          una tarjeta de crédito  
                          o débito para registrar 
                          una reserva.            

  RF-039                  El registro de la       Alta
                          tarjeta debe ser        
                          simulado y no debe      
                          realizar un cobro       
                          financiero real.        

  RF-040                  El sistema debe asociar Alta
                          cada reserva con el     
                          cliente que la realizó. 

  RF-041                  El sistema debe asociar Alta
                          cada reserva con el     
                          hotel y habitación      
                          seleccionados.          

  RF-042                  El sistema debe         Alta
                          registrar el período de 
                          alojamiento asociado a  
                          la reserva.             

  RF-043                  El sistema debe         Alta
                          permitir al cliente     
                          consultar su historial  
                          de reservas.            

  RF-044                  El sistema debe impedir Crítica
                          que un cliente consulte 
                          o gestione reservas     
                          pertenecientes a otro   
                          cliente.                
  -----------------------------------------------------------------------

### 1.5 Checkout y cobros

  -----------------------------------------------------------------------
  ID                      Requisito funcional     Prioridad
  ----------------------- ----------------------- -----------------------
  RF-045                  El sistema debe         Crítica
                          permitir al cliente     
                          realizar el checkout    
                          mediante la aplicación  
                          móvil.                  

  RF-046                  El sistema debe exigir  Alta
                          al cliente registrar    
                          una valoración sobre su 
                          estadía durante el      
                          checkout.               

  RF-047                  El sistema debe exigir  Alta
                          al cliente registrar    
                          observaciones sobre su  
                          estadía durante el      
                          checkout.               

  RF-048                  El sistema debe         Alta
                          notificar al            
                          administrador del hotel 
                          que un cliente ha       
                          realizado el checkout.  

  RF-049                  El administrador de     Alta
                          hotel debe poder        
                          realizar el cobro       
                          utilizando la tarjeta   
                          previamente registrada  
                          por el cliente.         

  RF-050                  El sistema debe         Alta
                          representar el cobro de 
                          forma simulada, sin     
                          procesar una            
                          transacción bancaria    
                          real.                   

  RF-051                  El administrador de     Alta
                          hotel debe poder        
                          registrar cobros        
                          adicionales asociados a 
                          una estadía.            

  RF-052                  Cada cobro adicional    Alta
                          debe registrar          
                          obligatoriamente el     
                          monto, motivo y         
                          observación.            

  RF-053                  El sistema debe         Alta
                          notificar al cliente    
                          cuando se haya          
                          realizado el cobro      
                          correspondiente.        

  RF-054                  El sistema debe asociar Alta
                          cada cobro con la       
                          reserva                 
                          correspondiente.        
  -----------------------------------------------------------------------

### 1.6 Reportes

  -----------------------------------------------------------------------
  ID                      Requisito funcional     Prioridad
  ----------------------- ----------------------- -----------------------
  RF-055                  El sistema debe generar Alta
                          reportes de reservas y  
                          ventas por hotel.       

  RF-056                  El sistema debe         Alta
                          permitir consultar      
                          reportes diarios.       

  RF-057                  El sistema debe         Alta
                          permitir consultar      
                          reportes mensuales.     

  RF-058                  El sistema debe         Alta
                          permitir consultar      
                          reportes anuales.       

  RF-059                  El Superadmin debe      Alta
                          poder consultar         
                          reportes de reservas    
                          por hotel.              

  RF-060                  El sistema debe generar Alta
                          un reporte de ingresos  
                          producidos por los      
                          servicios adicionales   
                          de un hotel.            

  RF-061                  El reporte de servicios Alta
                          adicionales debe        
                          ordenar los resultados  
                          de menor a mayor según  
                          el monto total          
                          generado.               
  -----------------------------------------------------------------------

### 1.7 Chat entre cliente y hotel

  -----------------------------------------------------------------------
  ID                      Requisito funcional     Prioridad
  ----------------------- ----------------------- -----------------------
  RF-062                  El sistema debe         Alta
                          proporcionar un chat    
                          privado entre el        
                          cliente y el hotel.     

  RF-063                  El cliente debe poder   Alta
                          enviar mensajes al      
                          hotel mientras tenga    
                          una reserva activa.     

  RF-064                  El administrador de     Alta
                          hotel debe poder        
                          visualizar y responder  
                          mensajes de clientes    
                          con una reserva activa  
                          en su hotel.            

  RF-065                  El sistema debe impedir Alta
                          el acceso al chat       
                          cuando la reserva haya  
                          finalizado mediante     
                          checkout.               

  RF-066                  El sistema debe         Alta
                          persistir los mensajes  
                          intercambiados.         

  RF-067                  Cada mensaje persistido Alta
                          debe registrar su fecha 
                          y hora.                 

  RF-068                  El sistema debe asociar Alta
                          cada conversación con   
                          el cliente y el hotel   
                          correspondientes.       
  -----------------------------------------------------------------------

### 1.8 Gestión de taxistas

  -----------------------------------------------------------------------
  ID                      Requisito funcional     Prioridad
  ----------------------- ----------------------- -----------------------
  RF-069                  El sistema web de       Alta
                          gestión de taxistas     
                          debe permitir el        
                          autoregistro de         
                          taxistas.               

  RF-070                  El registro del taxista Alta
                          debe permitir almacenar 
                          nombres y apellidos.    

  RF-071                  El registro del taxista Alta
                          debe permitir almacenar 
                          tipo y número de        
                          documento de identidad. 

  RF-072                  El registro del taxista Alta
                          debe permitir almacenar 
                          fecha de nacimiento.    

  RF-073                  El registro del taxista Alta
                          debe permitir almacenar 
                          correo electrónico,     
                          teléfono y domicilio.   

  RF-074                  El registro del taxista Alta
                          debe permitir almacenar 
                          una fotografía del      
                          taxista.                

  RF-075                  El registro del taxista Alta
                          debe permitir almacenar 
                          la placa del vehículo.  

  RF-076                  El registro del taxista Alta
                          debe permitir almacenar 
                          una fotografía del      
                          vehículo.               

  RF-077                  El Superadmin debe      Crítica
                          poder aprobar o         
                          habilitar un taxista    
                          antes de que pueda      
                          prestar servicios.      

  RF-078                  El sistema debe         Alta
                          permitir consultar el   
                          estado de habilitación  
                          del taxista.            

  RF-079                  El sistema debe         Alta
                          permitir consultar y    
                          actualizar la           
                          información del         
                          taxista.                

  RF-080                  El sistema debe         Alta
                          registrar las           
                          valoraciones realizadas 
                          por los clientes a los  
                          taxistas.               

  RF-081                  El sistema debe         Alta
                          calcular y proporcionar 
                          la valoración promedio  
                          de cada taxista.        

  RF-082                  El sistema debe         Crítica
                          permitir consultar      
                          taxistas disponibles    
                          mediante la API REST    
                          del sistema web de      
                          gestión de taxistas.    
  -----------------------------------------------------------------------

### 1.9 Servicio de taxi

  -----------------------------------------------------------------------
  ID                      Requisito funcional     Prioridad
  ----------------------- ----------------------- -----------------------
  RF-083                  El sistema debe ofrecer Alta
                          al cliente un servicio  
                          gratuito de taxi desde  
                          el hotel hacia un       
                          aeropuerto cuando se    
                          cumplan las condiciones 
                          establecidas.           

  RF-084                  El sistema debe         Alta
                          verificar que la        
                          reserva alcance el      
                          monto mínimo            
                          establecido por el      
                          hotel para acceder al   
                          beneficio.              

  RF-085                  El cliente debe poder   Alta
                          decidir si utiliza o no 
                          el servicio de taxi     
                          cuando tenga derecho al 
                          beneficio.              

  RF-086                  El cliente debe indicar Alta
                          el aeropuerto de        
                          destino al solicitar el 
                          servicio.               

  RF-087                  El sistema debe         Alta
                          registrar una solicitud 
                          de taxi asociada a la   
                          reserva                 
                          correspondiente.        

  RF-088                  El sistema debe         Alta
                          permitir a un taxista   
                          habilitado consultar    
                          los pedidos de taxi     
                          disponibles.            

  RF-089                  El sistema debe mostrar Alta
                          al taxista la           
                          información necesaria   
                          del pedido, incluyendo  
                          hotel de recogida y     
                          destino.                

  RF-090                  El taxista debe poder   Crítica
                          aceptar un pedido       
                          disponible.             

  RF-091                  Una vez aceptado un     Crítica
                          pedido, el sistema debe 
                          asignarlo al taxista    
                          que lo aceptó.          

  RF-092                  Una vez asignado un     Crítica
                          pedido, este debe dejar 
                          de estar disponible     
                          para los demás          
                          taxistas.               

  RF-093                  El sistema debe obtener Crítica
                          la información del      
                          taxista asignado        
                          mediante la API REST    
                          del sistema de gestión  
                          de taxistas.            

  RF-094                  El sistema debe         Alta
                          permitir consultar los  
                          datos del taxista       
                          asignado.               

  RF-095                  El sistema debe         Alta
                          permitir consultar la   
                          información del         
                          vehículo del taxista    
                          asignado.               

  RF-096                  El sistema debe         Alta
                          permitir consultar la   
                          valoración del taxista  
                          asignado.               

  RF-097                  El sistema debe         Alta
                          permitir consultar el   
                          estado actual del       
                          servicio de taxi.       

  RF-098                  El sistema debe         Crítica
                          registrar               
                          automáticamente la      
                          ubicación del taxista   
                          mediante su dispositivo 
                          móvil durante un        
                          servicio activo.        

  RF-099                  El cliente debe poder   Alta
                          visualizar la ubicación 
                          del taxista en un mapa  
                          durante el servicio.    

  RF-100                  El administrador del    Alta
                          hotel debe poder        
                          consultar el estado del 
                          servicio de taxi        
                          asociado a sus          
                          clientes.               

  RF-101                  El sistema debe generar Alta
                          o proporcionar al       
                          cliente un código QR    
                          asociado al servicio de 
                          taxi una vez asignado   
                          el taxista.             

  RF-102                  El taxista debe poder   Alta
                          leer el código QR       
                          mediante la aplicación  
                          móvil.                  

  RF-103                  El sistema debe validar Crítica
                          que el código QR        
                          corresponda al servicio 
                          activo antes de         
                          permitir su             
                          finalización.           

  RF-104                  La validación correcta  Crítica
                          del código QR debe      
                          cambiar el servicio al  
                          estado FINALIZADO.      

  RF-105                  El cliente debe poder   Alta
                          valorar al taxista una  
                          vez finalizado el       
                          servicio.               
  -----------------------------------------------------------------------

### 1.10 Estados del servicio de taxi

  -----------------------------------------------------------------------
  ID                      Requisito funcional     Prioridad
  ----------------------- ----------------------- -----------------------
  RF-106                  El sistema debe manejar Crítica
                          los estados SOLICITADO, 
                          ASIGNADO, EN CAMINO, EN 
                          TRASLADO y FINALIZADO.  

  RF-107                  Al aceptar un pedido,   Crítica
                          el sistema debe cambiar 
                          automáticamente el      
                          estado a ASIGNADO.      

  RF-108                  El taxista debe poder   Alta
                          cambiar el estado de    
                          ASIGNADO a EN CAMINO.   

  RF-109                  El taxista debe poder   Alta
                          cambiar el estado de EN 
                          CAMINO a EN TRASLADO.   

  RF-110                  El estado FINALIZADO    Crítica
                          solo debe poder         
                          registrarse mediante la 
                          validación correcta del 
                          código QR.              

  RF-111                  El sistema debe impedir Crítica
                          transiciones de estado  
                          que no correspondan al  
                          flujo definido del      
                          servicio.               
  -----------------------------------------------------------------------

### 1.11 API de gestión de taxistas

  -----------------------------------------------------------------------
  ID                      Requisito funcional     Prioridad
  ----------------------- ----------------------- -----------------------
  RF-112                  El sistema web de       Crítica
                          gestión de taxistas     
                          debe exponer una API    
                          REST para integrarse    
                          con la aplicación móvil 
                          principal.              

  RF-113                  La API REST debe        Alta
                          permitir consultar      
                          taxistas disponibles.   

  RF-114                  La API REST debe        Alta
                          permitir obtener        
                          información del taxista 
                          asignado.               

  RF-115                  La API REST debe        Alta
                          permitir consultar el   
                          estado del servicio de  
                          taxi.                   

  RF-116                  La API REST debe        Alta
                          permitir consultar la   
                          ubicación del taxista.  

  RF-117                  La aplicación móvil     Crítica
                          debe utilizar la API    
                          REST para todas las     
                          consultas y             
                          actualizaciones de      
                          información             
                          relacionadas con los    
                          taxistas.               
  -----------------------------------------------------------------------

### 1.12 Auditoría

  -----------------------------------------------------------------------
  ID                      Requisito funcional     Prioridad
  ----------------------- ----------------------- -----------------------
  RF-118                  El sistema debe         Alta
                          registrar los eventos   
                          relevantes ocurridos    
                          durante su operación.   

  RF-119                  El sistema debe         Alta
                          registrar como mínimo   
                          los eventos relevantes  
                          relacionados con        
                          autenticación,          
                          usuarios, reservas,     
                          checkout, cobros,       
                          servicios de taxi y     
                          cambios de estado.      

  RF-120                  El Superadmin debe      Alta
                          poder consultar los     
                          logs de eventos         
                          registrados.            
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 2. Requisitos de calidad

Los requisitos de calidad establecen **cómo debe comportarse el
sistema**, independientemente de una funcionalidad específica.

## 2.1 Seguridad

  -----------------------------------------------------------------------
  ID                      Requisito de calidad    Prioridad
  ----------------------- ----------------------- -----------------------
  RC-001                  Las contraseñas no      Crítica
                          deben almacenarse en    
                          texto plano. Deben      
                          almacenarse utilizando  
                          un mecanismo de hash    
                          seguro para             
                          contraseñas.            

  RC-002                  El sistema debe         Crítica
                          restringir las          
                          operaciones según el    
                          rol autenticado.        

  RC-003                  La autorización debe    Crítica
                          validarse en el lado    
                          servidor y no depender  
                          únicamente de las       
                          restricciones de la     
                          interfaz móvil.         

  RC-004                  Un cliente solo debe    Crítica
                          poder acceder a sus     
                          propias reservas.       

  RC-005                  Un cliente solo debe    Crítica
                          poder acceder a sus     
                          propios chats.          

  RC-006                  Un cliente solo debe    Crítica
                          poder acceder a sus     
                          propios pagos y cobros  
                          asociados.              

  RC-007                  Un cliente solo debe    Crítica
                          poder acceder a sus     
                          propios servicios de    
                          taxi.                   

  RC-008                  Un administrador de     Crítica
                          hotel solo debe poder   
                          gestionar información y 
                          operaciones             
                          correspondientes al     
                          hotel que administra.   

  RC-009                  Un taxista solo debe    Crítica
                          poder ejecutar          
                          operaciones             
                          correspondientes a su   
                          propia cuenta y a       
                          servicios que le hayan  
                          sido asignados.         

  RC-010                  Las interfaces de       Alta
                          comunicación entre la   
                          aplicación móvil y      
                          servicios remotos deben 
                          proteger las            
                          credenciales y datos    
                          transmitidos.           

  RC-011                  Los datos de            Alta
                          autenticación y         
                          autorización no deben   
                          exponerse               
                          innecesariamente al     
                          cliente móvil.          
  -----------------------------------------------------------------------

## 2.2 Integridad y consistencia

  -----------------------------------------------------------------------
  ID                      Requisito de calidad    Prioridad
  ----------------------- ----------------------- -----------------------
  RC-012                  El sistema debe         Crítica
                          garantizar que una      
                          habitación no pueda     
                          quedar reservada        
                          simultáneamente por dos 
                          clientes para períodos  
                          superpuestos.           

  RC-013                  La validación de        Crítica
                          disponibilidad debe     
                          mantenerse consistente  
                          incluso cuando dos      
                          clientes intenten       
                          reservar la misma       
                          habitación              
                          aproximadamente al      
                          mismo tiempo.           

  RC-014                  Un pedido de taxi solo  Crítica
                          puede estar asignado a  
                          un taxista a la vez.    

  RC-015                  El sistema debe impedir Crítica
                          que dos taxistas        
                          acepten simultáneamente 
                          el mismo pedido y ambos 
                          queden asignados.       

  RC-016                  Los cambios de estado   Crítica
                          del servicio de taxi    
                          deben respetar la       
                          secuencia de estados    
                          definida.               

  RC-017                  La finalización de un   Crítica
                          servicio mediante QR    
                          debe ser una operación  
                          válida, verificable y   
                          asociada al servicio    
                          correcto.               

  RC-018                  Las reservas, cobros,   Alta
                          cobros adicionales,     
                          mensajes y servicios de 
                          taxi deben conservar    
                          una asociación          
                          inequívoca con las      
                          entidades               
                          correspondientes.       
  -----------------------------------------------------------------------

## 2.3 Disponibilidad y resiliencia

  -----------------------------------------------------------------------
  ID                      Requisito de calidad    Prioridad
  ----------------------- ----------------------- -----------------------
  RC-019                  Si la API del sistema   Crítica
                          de gestión de taxistas  
                          no está disponible, la  
                          aplicación móvil debe   
                          informar al usuario del 
                          problema.               

  RC-020                  La indisponibilidad de  Crítica
                          la API de gestión de    
                          taxistas no debe        
                          impedir las demás       
                          funcionalidades del     
                          sistema de reservas.    

  RC-021                  Los fallos de           Alta
                          comunicación con        
                          servicios externos      
                          deben ser tratados de   
                          forma controlada y no   
                          provocar el cierre      
                          inesperado de la        
                          aplicación.             

  RC-022                  La aplicación debe      Alta
                          mostrar al usuario un   
                          estado comprensible     
                          cuando una operación    
                          dependiente de un       
                          servicio externo no     
                          pueda completarse.      

  RC-023                  El sistema debe evitar  Crítica
                          que una operación       
                          parcialmente ejecutada  
                          produzca datos          
                          inconsistentes.         
  -----------------------------------------------------------------------

## 2.4 Localización

  -----------------------------------------------------------------------
  ID                      Requisito de calidad    Prioridad
  ----------------------- ----------------------- -----------------------
  RC-024                  Durante un servicio     Crítica
                          activo, la ubicación    
                          del taxista debe        
                          actualizarse            
                          periódicamente.         

  RC-025                  La transmisión de       Alta
                          ubicación debe          
                          producirse únicamente   
                          mientras exista un      
                          servicio de taxi        
                          activo.                 

  RC-026                  El sistema debe tolerar Alta
                          temporalmente la        
                          pérdida de conectividad 
                          del dispositivo del     
                          taxista sin corromper   
                          el estado del servicio. 

  RC-027                  La aplicación debe      Media
                          indicar al cliente      
                          cuando la ubicación     
                          mostrada pueda estar    
                          desactualizada debido a 
                          falta de actualización. 
  -----------------------------------------------------------------------

## 2.5 Usabilidad

  -----------------------------------------------------------------------
  ID                      Requisito de calidad    Prioridad
  ----------------------- ----------------------- -----------------------
  RC-028                  Las operaciones         Alta
                          principales deben       
                          presentar flujos        
                          comprensibles y         
                          coherentes para cada    
                          rol.                    

  RC-029                  La aplicación debe      Alta
                          proporcionar mensajes   
                          claros ante errores de  
                          validación,             
                          autenticación,          
                          disponibilidad o        
                          comunicación.           

  RC-030                  Las operaciones         Alta
                          críticas, como reserva, 
                          checkout, aceptación de 
                          taxi y finalización del 
                          servicio, deben mostrar 
                          claramente su           
                          resultado.              

  RC-031                  La aplicación debe      Media
                          impedir que el usuario  
                          confirme                
                          accidentalmente         
                          operaciones críticas    
                          mediante controles de   
                          confirmación adecuados. 
  -----------------------------------------------------------------------

## 2.6 Rendimiento

  -----------------------------------------------------------------------
  ID                      Requisito de calidad    Prioridad
  ----------------------- ----------------------- -----------------------
  RC-032                  Las operaciones         Alta
                          normales de consulta y  
                          navegación no deben     
                          bloquear                
                          innecesariamente la     
                          interfaz de usuario.    

  RC-033                  Las operaciones de      Alta
                          comunicación con        
                          servicios remotos deben 
                          ejecutarse sin bloquear 
                          permanentemente la      
                          interfaz de la          
                          aplicación móvil.       

  RC-034                  La actualización        Alta
                          periódica de ubicación  
                          no debe impedir el uso  
                          normal de la aplicación 
                          móvil.                  
  -----------------------------------------------------------------------

## 2.7 Mantenibilidad

  -----------------------------------------------------------------------
  ID                      Requisito de calidad    Prioridad
  ----------------------- ----------------------- -----------------------
  RC-035                  El sistema debe         Alta
                          mantener una separación 
                          clara de                
                          responsabilidades entre 
                          sus componentes.        

  RC-036                  Las interfaces de       Alta
                          comunicación entre      
                          sistemas deben estar    
                          documentadas.           

  RC-037                  La API REST debe        Alta
                          disponer de             
                          documentación de sus    
                          operaciones,            
                          parámetros, respuestas  
                          y errores.              

  RC-038                  Las decisiones técnicas Media
                          relevantes deben quedar 
                          documentadas para       
                          facilitar el            
                          mantenimiento del       
                          sistema.                

  RC-039                  Los componentes deben   Alta
                          poder probarse de forma 
                          independiente cuando su 
                          responsabilidad lo      
                          permita.                
  -----------------------------------------------------------------------

## 2.8 Trazabilidad y auditoría

  -----------------------------------------------------------------------
  ID                      Requisito de calidad    Prioridad
  ----------------------- ----------------------- -----------------------
  RC-040                  Los eventos relevantes  Alta
                          deben conservar como    
                          mínimo una marca        
                          temporal.               

  RC-041                  Cuando corresponda, los Alta
                          eventos deben permitir  
                          identificar al usuario  
                          o componente que        
                          produjo la operación.   

  RC-042                  Los logs no deben       Crítica
                          contener contraseñas ni 
                          información sensible    
                          innecesaria.            

  RC-043                  Los registros de        Alta
                          auditoría deben         
                          permitir investigar     
                          operaciones relevantes  
                          del sistema.            
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 3. Restricciones

Las restricciones constituyen condiciones obligatorias que limitan las
alternativas de implementación y diseño.

## 3.1 Restricciones tecnológicas

  -----------------------------------------------------------------------
  ID                      Restricción             Prioridad
  ----------------------- ----------------------- -----------------------
  RT-001                  La aplicación principal Obligatoria
                          debe desarrollarse de   
                          forma nativa para       
                          Android.                

  RT-002                  La aplicación móvil     Obligatoria
                          debe desarrollarse      
                          utilizando Java.        

  RT-003                  La versión mínima       Obligatoria
                          soportada de Android    
                          debe ser Android 14,    
                          API Level 34.           

  RT-004                  La persistencia de la   Obligatoria
                          aplicación principal    
                          debe utilizar una base  
                          de datos NoSQL.         

  RT-005                  La aplicación móvil no  Obligatoria
                          debe acceder            
                          directamente a la base  
                          de datos del sistema    
                          web de gestión de       
                          taxistas.               

  RT-006                  Toda consulta o         Obligatoria
                          actualización de        
                          información relacionada 
                          con taxistas debe       
                          realizarse mediante la  
                          API REST proporcionada  
                          por el sistema web de   
                          gestión de taxistas.    

  RT-007                  El sistema web de       Obligatoria
                          gestión de taxistas     
                          debe mantenerse como    
                          una aplicación          
                          independiente de la     
                          aplicación móvil        
                          principal.              

  RT-008                  El sistema web de       Obligatoria
                          gestión de taxistas     
                          debe proporcionar una   
                          API REST para la        
                          integración con la      
                          aplicación móvil.       
  -----------------------------------------------------------------------

## 3.2 Restricciones de alcance

  -----------------------------------------------------------------------
  ID                      Restricción             Prioridad
  ----------------------- ----------------------- -----------------------
  RT-009                  El proyecto debe        Obligatoria
                          implementar las         
                          funcionalidades         
                          definidas para los      
                          roles Superadmin,       
                          administrador de hotel, 
                          taxista y cliente.      

  RT-010                  El pago con tarjeta     Obligatoria
                          debe ser simulado; no   
                          se requiere ni debe     
                          asumirse una            
                          integración con una     
                          entidad financiera      
                          real.                   

  RT-011                  El servicio de taxi     Obligatoria
                          contemplado corresponde 
                          al traslado desde el    
                          hotel hacia un          
                          aeropuerto.             

  RT-012                  El servicio de taxi es  Obligatoria
                          gratuito únicamente     
                          cuando la reserva       
                          alcanza el monto mínimo 
                          establecido por el      
                          hotel.                  

  RT-013                  El uso del servicio de  Obligatoria
                          taxi debe ser opcional  
                          para el cliente.        

  RT-014                  El taxista debe estar   Obligatoria
                          habilitado por el       
                          Superadmin antes de     
                          poder prestar           
                          servicios.              

  RT-015                  El checkout debe        Obligatoria
                          realizarse mediante la  
                          aplicación móvil.       

  RT-016                  La finalización del     Obligatoria
                          servicio de taxi debe   
                          realizarse mediante     
                          validación del código   
                          QR del cliente.         
  -----------------------------------------------------------------------

## 3.3 Restricciones de datos

  -----------------------------------------------------------------------
  ID                      Restricción             Prioridad
  ----------------------- ----------------------- -----------------------
  RT-017                  La aplicación principal Obligatoria
                          debe utilizar           
                          persistencia NoSQL.     

  RT-018                  La información          Obligatoria
                          necesaria para operar   
                          el sistema debe         
                          persistirse de forma    
                          que pueda recuperarse   
                          después de cerrar o     
                          reiniciar la            
                          aplicación.             

  RT-019                  Las fotografías del     Obligatoria
                          hotel, instalaciones,   
                          servicios, taxistas y   
                          vehículos deben         
                          gestionarse como        
                          información asociada a  
                          sus respectivas         
                          entidades.              

  RT-020                  Los mensajes del chat   Obligatoria
                          deben persistir y       
                          conservar su fecha y    
                          hora.                   

  RT-021                  Los cobros adicionales  Obligatoria
                          deben conservar monto,  
                          motivo y observación.   

  RT-022                  Los registros de        Obligatoria
                          reservas deben          
                          conservar la            
                          información necesaria   
                          para determinar su      
                          período de alojamiento  
                          y disponibilidad.       
  -----------------------------------------------------------------------

## 3.4 Restricciones de integración

  -----------------------------------------------------------------------
  ID                      Restricción             Prioridad
  ----------------------- ----------------------- -----------------------
  RT-023                  La aplicación móvil     Obligatoria
                          debe integrarse con el  
                          sistema de gestión de   
                          taxistas exclusivamente 
                          mediante su API REST.   

  RT-024                  La indisponibilidad del Obligatoria
                          sistema de gestión de   
                          taxistas no debe        
                          inutilizar las          
                          funcionalidades         
                          independientes de       
                          gestión de reservas.    

  RT-025                  La integración debe     Obligatoria
                          permitir consultar,     
                          como mínimo, taxistas   
                          disponibles,            
                          información del taxista 
                          asignado, estado del    
                          servicio y ubicación.   

  RT-026                  El sistema móvil y el   Obligatoria
                          sistema web de taxistas 
                          deben conservar         
                          independencia respecto  
                          de sus bases de datos.  
  -----------------------------------------------------------------------

## 3.5 Restricciones de despliegue y entrega

  -----------------------------------------------------------------------
  ID                      Restricción             Prioridad
  ----------------------- ----------------------- -----------------------
  RT-027                  El sistema debe         Obligatoria
                          desplegarse en la nube. 

  RT-028                  El repositorio del      Obligatoria
                          proyecto debe contener  
                          el código de cada       
                          elemento del sistema.   

  RT-029                  Deben entregarse los    Obligatoria
                          artefactos de base de   
                          datos solicitados por   
                          el proyecto.            

  RT-030                  Debe entregarse un      Obligatoria
                          archivo de arquitectura 
                          del sistema.            

  RT-031                  Debe documentarse el    Obligatoria
                          costo total de la       
                          solución y su OPEX.     

  RT-032                  Debe entregarse un      Obligatoria
                          manual de instalación.  

  RT-033                  Debe entregarse un      Obligatoria
                          manual de usuario.      
  -----------------------------------------------------------------------

## 3.6 Restricciones de seguridad y privacidad

  -----------------------------------------------------------------------
  ID                      Restricción             Prioridad
  ----------------------- ----------------------- -----------------------
  RT-034                  Las contraseñas no      Obligatoria
                          pueden almacenarse en   
                          texto plano.            

  RT-035                  El acceso a las         Obligatoria
                          operaciones debe estar  
                          condicionado por el rol 
                          autenticado.            

  RT-036                  Los datos de un cliente Obligatoria
                          no deben quedar         
                          accesibles a otros      
                          clientes.               

  RT-037                  Los datos de ubicación  Obligatoria
                          del taxista deben       
                          utilizarse para la      
                          operación del servicio  
                          activo y no deben       
                          mantenerse              
                          innecesariamente una    
                          vez finalizado el       
                          servicio.               

  RT-038                  Los logs no deben       Obligatoria
                          almacenar credenciales  
                          ni secretos de          
                          autenticación.          
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 4. Resumen cuantitativo

  Categoría                  Cantidad
  ------------------------ ----------
  Requisitos funcionales          120
  Requisitos de calidad            43
  Restricciones                    38
  **Total**                   **201**

------------------------------------------------------------------------

# 5. Prioridad de los requisitos

Se utilizará la siguiente clasificación:

  -----------------------------------------------------------------------
  Prioridad                           Significado
  ----------------------------------- -----------------------------------
  **Crítica**                         Su incumplimiento compromete una
                                      función esencial, la integridad de
                                      los datos, la seguridad o una
                                      restricción fundamental del
                                      sistema.

  **Alta**                            Es necesaria para cumplir
                                      adecuadamente el alcance principal
                                      del proyecto.

  **Media**                           Complementa el sistema o mejora una
                                      capacidad que no constituye el
                                      núcleo de operación.

  **Obligatoria**                     Restricción que no puede ser
                                      incumplida por una decisión de
                                      implementación.
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 6. Criterio general de verificación

Un requisito se considerará satisfecho únicamente cuando exista
evidencia de que:

1.  la funcionalidad o propiedad está implementada;
2.  el comportamiento esperado puede reproducirse;
3.  se cumplen sus condiciones y restricciones;
4.  no se vulneran requisitos de seguridad asociados;
5.  la implementación no contradice otros requisitos;
6.  existe una prueba o demostración que permita verificarlo.

------------------------------------------------------------------------

# 7. Fuente

La especificación se deriva del **Plan de proyecto -- Sistema para la
gestión de reservas de alojamiento en hoteles vía aplicación móvil**,
especialmente de las secciones de descripción del sistema,
funcionalidades por actor, gestión de taxistas, restricciones
tecnológicas y requerimientos de calidad. El Plan establece
explícitamente la aplicación móvil Android/Java, Android 14/API 34,
persistencia NoSQL y la integración del sistema de taxistas mediante API
REST.
