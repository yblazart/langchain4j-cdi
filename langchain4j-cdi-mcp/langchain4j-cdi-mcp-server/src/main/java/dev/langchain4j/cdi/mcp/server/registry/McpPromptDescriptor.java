package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.api.McpFrameworkTypes;
import dev.langchain4j.cdi.mcp.server.protocol.McpIconModel;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.mcpjava.server.FeatureType;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;

/**
 * Describes an MCP prompt extracted from a {@link Prompt}-annotated method, including its name, description, arguments,
 * owning bean type, and target method.
 */
public class McpPromptDescriptor {

    private static final String DEFAULT_NAME = "<<element name>>";

    private final String name;
    private final String description;
    private final List<PromptArgument> arguments;
    private final Class<?> beanType;
    private final Method method;
    private final List<McpIconModel> icons;

    /**
     * Creates a new prompt descriptor carrying no icons.
     *
     * @param name the prompt name
     * @param description the prompt description
     * @param arguments the list of prompt arguments
     * @param beanType the CDI bean class that declares the prompt method
     * @param method the annotated method
     */
    public McpPromptDescriptor(
            String name, String description, List<PromptArgument> arguments, Class<?> beanType, Method method) {
        this(name, description, arguments, beanType, method, null);
    }

    /**
     * Creates a new prompt descriptor.
     *
     * @param name the prompt name
     * @param description the prompt description
     * @param arguments the list of prompt arguments
     * @param beanType the CDI bean class that declares the prompt method
     * @param method the annotated method
     * @param icons the icons resolved from {@code @Icons} at registration time, or {@code null} when there are none
     */
    public McpPromptDescriptor(
            String name,
            String description,
            List<PromptArgument> arguments,
            Class<?> beanType,
            Method method,
            List<McpIconModel> icons) {
        this.name = name;
        this.description = description;
        this.arguments = arguments;
        this.beanType = beanType;
        this.method = method;
        this.icons = icons;
    }

    /**
     * Creates a descriptor by inspecting a {@link Prompt}-annotated method and its parameters.
     *
     * @param beanClass the CDI bean class that declares the method
     * @param method the {@code @Prompt}-annotated method
     * @return a new descriptor built from the method's annotation metadata
     */
    public static McpPromptDescriptor fromMethod(Class<?> beanClass, Method method) {
        Prompt annotation = method.getAnnotation(Prompt.class);
        String name = DEFAULT_NAME.equals(annotation.name()) ? method.getName() : annotation.name();

        List<PromptArgument> args = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            if (McpFrameworkTypes.isFrameworkType(param.getType())) {
                // injected by the runtime (McpLog, Progress, Elicitation, ...), never asked of the client
                continue;
            }
            PromptArg argAnnotation = param.getAnnotation(PromptArg.class);
            String argDescription = argAnnotation != null ? argAnnotation.description() : "";
            boolean required = argAnnotation == null || argAnnotation.required();
            args.add(new PromptArgument(argumentName(param, argAnnotation), argDescription, required));
        }

        return new McpPromptDescriptor(
                name,
                annotation.description(),
                args,
                beanClass,
                method,
                McpIconResolver.resolve(FeatureType.PROMPT, name, beanClass, method));
    }

    /**
     * Returns the icons resolved from an {@code org.mcpjava.server.Icons} annotation at registration time.
     *
     * @return the icons, or {@code null} when the prompt declares none
     */
    public List<McpIconModel> getIcons() {
        return icons;
    }

    /**
     * Returns the wire name of a prompt argument: the {@code name} of its {@code @PromptArg} when it sets one,
     * otherwise the Java parameter name (which needs the declaring module to be compiled with {@code -parameters}, or
     * it is {@code arg0}).
     *
     * @param param the prompt method parameter
     * @param annotation the parameter's {@code @PromptArg}, or {@code null}
     * @return the argument name to advertise
     */
    private static String argumentName(Parameter param, PromptArg annotation) {
        return annotation != null && !DEFAULT_NAME.equals(annotation.name()) ? annotation.name() : param.getName();
    }

    /**
     * Returns the prompt name.
     *
     * @return the prompt name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the prompt description.
     *
     * @return the prompt description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the list of prompt arguments.
     *
     * @return the list of prompt arguments
     */
    public List<PromptArgument> getArguments() {
        return arguments;
    }

    /**
     * Returns the CDI bean class that declares the prompt method.
     *
     * @return the bean class
     */
    public Class<?> getBeanType() {
        return beanType;
    }

    /**
     * Returns the annotated method that implements this prompt.
     *
     * @return the prompt method
     */
    public Method getMethod() {
        return method;
    }

    /**
     * A prompt argument descriptor.
     *
     * @param name the argument name
     * @param description the argument description
     * @param required whether the argument is required
     */
    public record PromptArgument(String name, String description, boolean required) {}
}
