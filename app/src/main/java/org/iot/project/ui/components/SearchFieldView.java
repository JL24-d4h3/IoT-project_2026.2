package org.iot.project.ui.components;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import org.iot.project.R;

/**
 * Campo de busqueda de texto.
 *
 * <p>Es el campo prominente de §14. Existe como componente porque el mismo
 * comportamiento —icono, borrar, avisar de cada tecla— hace falta en la
 * pantalla de destino y, mas adelante, en la busqueda de clientes del panel del
 * administrador. Repetirlo seria repetir tambien el detalle de que el aspa
 * desaparece cuando no hay nada que borrar.
 *
 * <p>No decide nada: quien lo usa recibe el texto y filtra. Un campo que
 * filtrara por su cuenta no se podria reutilizar para buscar otra cosa.
 */
public class SearchFieldView extends LinearLayout {

    private final EditText entrada;
    private final ImageButton limpiar;

    private OnQueryChangeListener oyente;

    /** Se avisa en cada tecla, con el texto tal como quedo. */
    public interface OnQueryChangeListener {
        void onQueryChange(@NonNull String texto);
    }

    public SearchFieldView(@NonNull Context context) {
        this(context, null);
    }

    public SearchFieldView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchFieldView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(ContextCompat.getDrawable(context, R.drawable.bg_search_field));

        int h = getResources().getDimensionPixelSize(R.dimen.space_md);
        setPadding(h, 0, 0, 0);

        LayoutInflater.from(context).inflate(R.layout.view_search_field, this, true);

        entrada = findViewById(R.id.search_input);
        limpiar = findViewById(R.id.search_clear);

        entrada.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                limpiar.setVisibility(s.length() > 0 ? VISIBLE : GONE);
                if (oyente != null) {
                    oyente.onQueryChange(s.toString());
                }
            }
        });

        // Buscar en el teclado cierra el teclado en lugar de no hacer nada: el
        // filtrado ya ocurre con cada tecla, asi que la tecla solo puede
        // confirmar y quitarse de en medio.
        entrada.setOnEditorActionListener((v, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_SEARCH) {
                ocultarTeclado();
                return true;
            }
            return false;
        });

        limpiar.setOnClickListener(v -> limpiarTexto());
    }

    public void setPista(@StringRes int texto) {
        entrada.setHint(texto);
    }

    @NonNull
    public String getTexto() {
        return entrada.getText().toString();
    }

    public void setTexto(@Nullable String texto) {
        entrada.setText(texto == null ? "" : texto);
        // setText no mueve el cursor al final si el texto viene de fuera.
        entrada.setSelection(entrada.getText().length());
    }

    public void setOnQueryChangeListener(@Nullable OnQueryChangeListener oyente) {
        this.oyente = oyente;
    }

    /** Deja el campo listo para escribir: es lo primero que se hace al entrar. */
    public void enfocar() {
        entrada.requestFocus();
        InputMethodManager teclado =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (teclado != null) {
            teclado.showSoftInput(entrada, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void ocultarTeclado() {
        InputMethodManager teclado =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (teclado != null) {
            teclado.hideSoftInputFromWindow(entrada.getWindowToken(), 0);
        }
    }

    /**
     * Vacía el campo avisando al oyente una sola vez. Se llama al pulsar el
     * aspa y tambien desde fuera, al elegir un destino.
     */
    public void limpiarTexto() {
        if (entrada.getText().length() == 0) {
            return;
        }
        entrada.setText("");
    }

    @NonNull
    public EditText getEntrada() {
        return entrada;
    }

    @NonNull
    public View getBotonLimpiar() {
        return limpiar;
    }
}
