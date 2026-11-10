package com.taxi.gestion.web;

import com.taxi.gestion.domain.enums.TipoDocumento;
import com.taxi.gestion.dto.request.RegistroTaxistaRequest;
import com.taxi.gestion.exception.RecursoDuplicadoException;
import com.taxi.gestion.exception.ReglaNegocioException;
import com.taxi.gestion.service.TaxistaService;
import com.taxi.gestion.storage.AlmacenamientoException;
import com.taxi.gestion.storage.ValidadorImagen;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Formulario publico de autoregistro del taxista (multipart: incluye las dos fotos). */
@Controller
@RequestMapping("/registro")
@RequiredArgsConstructor
public class RegistroWebController {

    private final TaxistaService taxistaService;
    private final ValidadorImagen validadorImagen;

    @ModelAttribute("tiposDocumento")
    public TipoDocumento[] tiposDocumento() {
        return TipoDocumento.values();
    }

    @GetMapping
    public String mostrarFormulario(Model model) {
        if (!model.containsAttribute("registroForm")) {
            model.addAttribute("registroForm", new RegistroTaxistaRequest());
        }
        return "registro";
    }

    @PostMapping
    public String procesar(@Valid @ModelAttribute("registroForm") RegistroTaxistaRequest form,
                           BindingResult binding,
                           Model model,
                           RedirectAttributes flash) {
        // Las fotos se validan antes de revisar errores, asi el taxista ve todos los
        // problemas del formulario de una sola vez (campos de texto y fotos).
        validarFoto(form.getFotoPerfil(), "fotoPerfil", "La foto del taxista", binding);
        validarFoto(form.getFotoVehiculo(), "fotoVehiculo", "La foto del vehiculo", binding);

        if (binding.hasErrors()) {
            return "registro";
        }
        try {
            taxistaService.registrar(form);
        } catch (RecursoDuplicadoException | ReglaNegocioException ex) {
            model.addAttribute("errorGeneral", ex.getMessage());
            return "registro";
        } catch (AlmacenamientoException ex) {
            model.addAttribute("errorGeneral",
                    "No pudimos guardar tus fotos. Intenta de nuevo en unos minutos.");
            return "registro";
        }
        flash.addFlashAttribute("exito",
                "Registro enviado. Revisaremos tus documentos y te avisaremos por correo "
                        + "cuando tu cuenta quede habilitada.");
        return "redirect:/registro";
    }

    private void validarFoto(MultipartFile foto, String campo, String etiqueta,
                             BindingResult binding) {
        try {
            validadorImagen.validar(foto, etiqueta);
        } catch (ReglaNegocioException ex) {
            binding.rejectValue(campo, "foto.invalida", ex.getMessage());
        }
    }
}
