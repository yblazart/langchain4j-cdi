package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.protocol.McpIconModel;
import dev.langchain4j.cdi.mcp.server.schema.McpParameterNames;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mcpjava.server.FeatureType;
import org.mcpjava.server.resources.ResourceTemplate;

/**
 * Describes an MCP resource template extracted from a {@link ResourceTemplate}-annotated method, including its URI
 * template, name, description, MIME type, owning bean type, and target method.
 *
 * <p>The descriptor also resolves the template against a concrete URI: {@link #match(String)} turns the RFC 6570
 * level-1 {@code {var}} expressions of the URI template into capturing groups and returns the variable values of a
 * matching URI, so that {@code resources/read} can serve an instance of the template. A variable in the middle of the
 * template matches a single path segment (level-1 expansion percent-encodes {@code /}); a variable that ends the
 * template matches the rest of the URI.
 */
public class McpResourceTemplateDescriptor {

    private static final Logger LOGGER = Logger.getLogger(McpResourceTemplateDescriptor.class.getName());

    private static final String DEFAULT_NAME = McpParameterNames.DEFAULT_ELEMENT_NAME;

    private static final Pattern VARIABLE = Pattern.compile("\\{([^{}/]+)}");

    private final String uriTemplate;
    private final String name;
    private final String description;
    private final String mimeType;
    private final Class<?> beanType;
    private final Method method;
    private final List<String> variableNames;
    private final Pattern uriPattern;
    private final List<McpIconModel> icons;

    /**
     * Creates a new resource template descriptor carrying no icons.
     *
     * @param uriTemplate the URI template pattern
     * @param name the resource template name
     * @param description the resource template description
     * @param mimeType the MIME type of the resource content
     * @param beanType the CDI bean class that declares the resource template method
     * @param method the annotated method
     */
    public McpResourceTemplateDescriptor(
            String uriTemplate, String name, String description, String mimeType, Class<?> beanType, Method method) {
        this(uriTemplate, name, description, mimeType, beanType, method, null);
    }

    /**
     * Creates a new resource template descriptor.
     *
     * @param uriTemplate the URI template pattern
     * @param name the resource template name
     * @param description the resource template description
     * @param mimeType the MIME type of the resource content
     * @param beanType the CDI bean class that declares the resource template method
     * @param method the annotated method
     * @param icons the icons resolved from {@code @Icons} at registration time, or {@code null} when there are none
     */
    public McpResourceTemplateDescriptor(
            String uriTemplate,
            String name,
            String description,
            String mimeType,
            Class<?> beanType,
            Method method,
            List<McpIconModel> icons) {
        this.icons = icons;
        this.uriTemplate = uriTemplate;
        this.name = name;
        this.description = description;
        this.mimeType = mimeType;
        this.beanType = beanType;
        this.method = method;
        this.variableNames = new ArrayList<>();
        this.uriPattern = compile(uriTemplate, this.variableNames);
    }

    private static Pattern compile(String uriTemplate, List<String> variableNames) {
        if (uriTemplate == null) {
            return null;
        }
        StringBuilder regex = new StringBuilder();
        Matcher matcher = VARIABLE.matcher(uriTemplate);
        int last = 0;
        while (matcher.find()) {
            regex.append(Pattern.quote(uriTemplate.substring(last, matcher.start())));
            variableNames.add(matcher.group(1));
            // a variable ending the template stands for the rest of the URI; elsewhere it is one path segment,
            // since RFC 6570 level-1 expansion percent-encodes '/'
            regex.append(matcher.end() == uriTemplate.length() ? "(.+)" : "([^/]+)");
            last = matcher.end();
        }
        regex.append(Pattern.quote(uriTemplate.substring(last)));
        return Pattern.compile(regex.toString());
    }

    /**
     * Matches a concrete URI against this template and extracts its variables.
     *
     * @param uri the requested resource URI
     * @return the percent-decoded template variables by name, in template order, or an empty {@link Optional} if the
     *     URI is not an instance of this template
     */
    public Optional<Map<String, String>> match(String uri) {
        if (uri == null || uriPattern == null) {
            return Optional.empty();
        }
        Matcher matcher = uriPattern.matcher(uri);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < variableNames.size(); i++) {
            variables.put(variableNames.get(i), decode(matcher.group(i + 1)));
        }
        return Optional.of(variables);
    }

    /**
     * Percent-decodes a captured variable, tolerating a malformed escape. A client is free to send any string as a
     * resource URI, and {@code URLDecoder} throws {@link IllegalArgumentException} on an incomplete trailing escape
     * ({@code 100%}) or on illegal hex characters ({@code a%zz}); letting that escape would leave the transport with a
     * container HTTP 500 carrying no JSON-RPC body. A value that cannot be decoded is passed through raw, so the match
     * still succeeds and the request is answered normally.
     *
     * @param value the raw captured value
     * @return the decoded value, or the raw value when it is not valid percent-encoding
     */
    private static String decode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        try {
            // URLDecoder also maps '+' to a space, which is a form-encoding rule and not a URI one
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.FINE, "URI variable contains malformed percent-encoding, using raw value: {0}", value);
            return value;
        }
    }

    /**
     * Returns the names of the variables of the URI template, in the order they appear.
     *
     * @return the template variable names
     */
    public List<String> getVariableNames() {
        return List.copyOf(variableNames);
    }

    /**
     * Creates a descriptor by inspecting a {@link ResourceTemplate}-annotated method.
     *
     * @param beanClass the CDI bean class that declares the method
     * @param method the {@code @ResourceTemplate}-annotated method
     * @return a new descriptor built from the method's annotation metadata
     */
    public static McpResourceTemplateDescriptor fromMethod(Class<?> beanClass, Method method) {
        ResourceTemplate annotation = method.getAnnotation(ResourceTemplate.class);
        String name = DEFAULT_NAME.equals(annotation.name()) ? method.getName() : annotation.name();
        String mimeType = annotation.mimeType().isEmpty() ? "text/plain" : annotation.mimeType();
        return new McpResourceTemplateDescriptor(
                annotation.uriTemplate(),
                name,
                annotation.description(),
                mimeType,
                beanClass,
                method,
                McpIconResolver.resolve(FeatureType.RESOURCE_TEMPLATE, name, beanClass, method));
    }

    /**
     * Returns the icons resolved from an {@code org.mcpjava.server.Icons} annotation at registration time.
     *
     * @return the icons, or {@code null} when the resource template declares none
     */
    public List<McpIconModel> getIcons() {
        return icons;
    }

    /**
     * Returns the URI template pattern.
     *
     * @return the URI template
     */
    public String getUriTemplate() {
        return uriTemplate;
    }

    /**
     * Returns the resource template name.
     *
     * @return the template name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the resource template description.
     *
     * @return the template description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the MIME type of the resource content.
     *
     * @return the MIME type
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Returns the CDI bean class that declares the resource template method.
     *
     * @return the bean class
     */
    public Class<?> getBeanType() {
        return beanType;
    }

    /**
     * Returns the annotated method that provides this resource template.
     *
     * @return the resource template method
     */
    public Method getMethod() {
        return method;
    }
}
