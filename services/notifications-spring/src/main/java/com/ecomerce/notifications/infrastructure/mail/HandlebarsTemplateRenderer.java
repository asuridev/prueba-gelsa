package com.ecomerce.notifications.infrastructure.mail;

import com.ecomerce.notifications.application.port.out.TemplateRenderer;
import com.ecomerce.notifications.domain.mail.TemplateRenderException;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Renderizado con Handlebars. La elección de motor es una decisión de SEGURIDAD y
 * está razonada aquí porque es lo primero que alguien querría cambiar.
 *
 * <p>Lo natural en Spring sería Thymeleaf, pero Thymeleaf está pensado para
 * plantillas <i>que escribes tú</i>: evalúa expresiones SpEL, y SpEL puede invocar
 * métodos arbitrarios. Con plantillas que entran por una API y rellenan equipos
 * ajenos, eso es una ejecución remota de código esperando a suceder. Handlebars solo
 * sustituye variables, recorre listas y evalúa condiciones simples: no hay forma de
 * llamar a nada.
 *
 * <p>Se paga en expresividad, y es más una virtud que un defecto: el formato de un
 * importe tiene reglas de locale y se prueba mucho mejor en el sistema llamante que
 * dentro de una cadena de texto guardada en una fila. Las variables llegan ya
 * formateadas.
 *
 * <p><b>No se añaden helpers</b> que salgan de esa frontera (nada de {@code
 * StringHelpers} con acceso a la JVM, nada de resolvers de fichero): cada helper
 * nuevo es superficie que quien escribe la plantilla puede alcanzar.
 */
@Component
public class HandlebarsTemplateRenderer implements TemplateRenderer {

    /**
     * Dos motores sin resolvers: el cuerpo llega como cadena y no hay nada que buscar en
     * el classpath ni en disco (un resolver de fichero convertiría un {@code
     * {{> ../../etc/passwd}}} en una lectura de fichero). Difieren SOLO en el escapado:
     * HTML para la parte HTML, ninguno para el texto y el asunto.
     */
    private final Handlebars html = new Handlebars().with(HTML_FIVE);
    private final Handlebars plain = new Handlebars().with(EscapingStrategy.NOOP);

    /**
     * El escapado HTML, con una tabla CERRADA: {@code & < > " '} → {@code &amp; &lt; &gt; &quot;
     * &#39;}. Es el conjunto que basta para escribir un dato como texto dentro de un elemento o de un
     * atributo entrecomillado, y es contrato: un escenario que afirma el cuerpo HTML afirma
     * exactamente esto.
     *
     * <p>No es el de {@code new Handlebars()}, y la diferencia se ve: aquel escribe el apóstrofo
     * como {@code &#x27;} y escapa además {@code `} y {@code =}, así que un {@code O'Hara} salía
     * {@code O&#x27;Hara} y el escenario que lo afirma con {@code &#39;} fallaba con un servidor
     * correcto (corrida notifications, 2026-09-30).
     */
    static final EscapingStrategy HTML_FIVE = new EscapingStrategy.Hbs(new String[][] {
        {"&", "&amp;"}, {"<", "&lt;"}, {">", "&gt;"}, {"\"", "&quot;"}, {"'", "&#39;"}
    });

    /**
     * Un marcador simple —{@code {{ nombre }}}— se compila como {@code {{[nombre]}}}: el segmento
     * literal, que Handlebars resuelve SIEMPRE como variable.
     *
     * <p>Sin esto, el nombre de la variable compite con el motor: {@code else} y {@code true} no
     * compilan; {@code log}, {@code if}, {@code each}, {@code with} y {@code unless} se resuelven
     * como helpers y salen VACÍOS, sin error; y {@code this} o {@code lookup} vuelcan el mapa entero
     * de variables en el correo. Medido contra handlebars 4.4.0. El diseño no conoce esas palabras
     * —declara sus variables con su propio formato— y no tiene por qué: el motor es mecánica del
     * generador.
     *
     * <p>Si la plantilla usa bloques ({@code {{#…}}}, {@code {{^…}}}), {@code else} y {@code this}
     * conservan su sentido de Handlebars: ahí son sintaxis del bloque, no variables.
     */
    private static final Pattern SIMPLE_MARKER =
            Pattern.compile("(?<!\\{)\\{\\{\\s*([A-Za-z_][A-Za-z0-9_-]*)\\s*}}(?!})");

    static String literalMarkers(String source) {
        if (source == null) {
            return null;
        }
        boolean blocks = source.contains("{{#") || source.contains("{{^");
        return SIMPLE_MARKER.matcher(source).replaceAll(match -> {
            String name = match.group(1);
            if (blocks && (name.equals("else") || name.equals("this"))) {
                return Matcher.quoteReplacement(match.group());
            }
            return Matcher.quoteReplacement("{{[" + name + "]}}");
        });
    }

    /** Techo de la caché: las plantillas vivas de un servicio son decenas, no miles. */
    private static final int MAX_COMPILED = 500;

    /**
     * Compilación cacheada por parte + identidad de plantilla, en un LRU acotado. La parte
     * va en la clave porque el mismo texto compilado con uno u otro motor escapa distinto;
     * y el techo existe porque la clave la aporta quien llama —con la versión dentro—, así
     * que sin él cada versión publicada se quedaba en memoria para siempre.
     */
    private final Map<String, Template> compiled = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Template> eldest) {
                    return size() > MAX_COMPILED;
                }
            });

    @Override
    public String render(Part part, String cacheKey, String source, Map<String, Object> variables) {
        try {
            Handlebars engine = part == Part.HTML ? html : plain;
            Template template = compiled.computeIfAbsent(part + ":" + cacheKey, key -> compileWith(engine, source));
            return template.apply(variables == null ? Map.of() : variables);
        } catch (IOException | RuntimeException e) {
            throw new TemplateRenderException("No se pudo renderizar la plantilla " + cacheKey + " (" + part + ")", e);
        }
    }

    @Override
    public void compile(String source) {
        // La sintaxis es la misma para los dos motores: basta uno.
        compileWith(plain, source);
    }

    private static Template compileWith(Handlebars engine, String source) {
        try {
            return engine.compileInline(literalMarkers(source));
        } catch (IOException | RuntimeException e) {
            throw new TemplateRenderException("La plantilla no compila", e);
        }
    }
}
