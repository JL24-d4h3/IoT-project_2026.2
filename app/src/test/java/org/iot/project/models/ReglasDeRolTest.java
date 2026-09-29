package org.iot.project.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** RF-006: el panel desactiva cuentas, pero no la de un superadministrador. */
public class ReglasDeRolTest {

    @Test
    public void unSuperadministradorNoSeDesactiva() {
        assertFalse(new User("U3", "Ana", "Ferreyra", Role.SUPERADMIN).esDesactivable());
    }

    @Test
    public void unClienteSeDesactiva() {
        assertTrue(new User("U1", "Lucía", "Quispe", Role.CLIENTE).esDesactivable());
    }

    @Test
    public void unAdministradorDeHotelSeDesactiva() {
        assertTrue(new User("U2", "Martín", "Rojas", Role.ADMIN_HOTEL).esDesactivable());
    }
}
