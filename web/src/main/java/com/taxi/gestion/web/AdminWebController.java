package com.taxi.gestion.web;

import com.taxi.gestion.domain.Taxista;
import com.taxi.gestion.domain.enums.EstadoHabilitacion;
import com.taxi.gestion.exception.ConflictoEstadoException;
import com.taxi.gestion.exception.RecursoNoEncontradoException;
import com.taxi.gestion.service.TaxistaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Panel del Superadmin. Requiere rol SUPERADMIN (ver SecurityConfig). */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminWebController {

    private final TaxistaService taxistaService;

    @GetMapping("/taxistas")
    public String listar(Model model) {
        List<Taxista> taxistas = taxistaService.listarTodos();
        model.addAttribute("taxistas", taxistas);
        model.addAttribute("totalPendientes", taxistas.stream()
                .filter(t -> t.getEstadoHabilitacion() == EstadoHabilitacion.PENDIENTE).count());
        model.addAttribute("totalHabilitados", taxistas.stream()
                .filter(t -> t.getEstadoHabilitacion() == EstadoHabilitacion.HABILITADO).count());
        model.addAttribute("totalInactivos", taxistas.stream()
                .filter(t -> !Boolean.TRUE.equals(t.getCuentaActiva())).count());
        return "admin/taxistas";
    }

    @PostMapping("/taxistas/{id}/habilitacion")
    public String cambiarHabilitacion(@PathVariable Long id,
                                      @org.springframework.web.bind.annotation.RequestParam
                                              EstadoHabilitacion estado,
                                      RedirectAttributes flash) {
        try {
            Taxista t = taxistaService.cambiarHabilitacion(id, estado);
            flash.addFlashAttribute("exito", estado == EstadoHabilitacion.HABILITADO
                    ? t.getNombreCompleto() + " quedo habilitado."
                    : t.getNombreCompleto() + " volvio a revision.");
        } catch (RecursoNoEncontradoException | ConflictoEstadoException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/taxistas";
    }

    @PostMapping("/taxistas/{id}/cuenta")
    public String cambiarCuenta(@PathVariable Long id,
                                @org.springframework.web.bind.annotation.RequestParam boolean activa,
                                RedirectAttributes flash) {
        try {
            Taxista t = taxistaService.cambiarEstadoCuenta(id, activa);
            flash.addFlashAttribute("exito", "Cuenta de " + t.getNombreCompleto()
                    + (activa ? " activada." : " desactivada."));
        } catch (RecursoNoEncontradoException | ConflictoEstadoException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/taxistas";
    }
}
