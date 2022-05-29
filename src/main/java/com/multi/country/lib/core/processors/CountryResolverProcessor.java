package com.multi.country.lib.core.processors;

import com.google.auto.service.AutoService;
import org.springframework.stereotype.Component;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.servlet.http.HttpServletRequest;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;


@SupportedAnnotationTypes("com.multi.country.lib.core.annotations.CountryResolver")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class CountryResolverProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {

            List<? extends Element> annotatedElements = new ArrayList<>(roundEnv.getElementsAnnotatedWith(annotation));

            annotatedElements.stream().map(x -> (TypeElement) x)
                    .forEach(
                        currClass -> {
                            try {
                                writeBuilderFile(currClass);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    );
        }

        return true;
    }

    private static final String IMPORT_TEMPLATE = "import %s;";

    private void writeBuilderFile(TypeElement classDefinition) throws IOException {

        String className = classDefinition.getQualifiedName().toString();
        String packageName = null;
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = className.substring(0, lastDot);
        }

        String simpleClassName = className.substring(lastDot + 1);
        String resolverClassName = simpleClassName + "Resolver";

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(resolverClassName);
        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {

            if (packageName != null) {
                out.print("package ");
                out.print(packageName);
                out.println(";");
                out.println();
            }

            out.println(format(IMPORT_TEMPLATE, CountryResolver.class.getName()));
            out.println();
            out.println(format(IMPORT_TEMPLATE, Component.class.getName()));
            out.println();
            out.println(format(IMPORT_TEMPLATE, HttpServletRequest.class.getName()));
            out.println(format(IMPORT_TEMPLATE, List.class.getName()));
            out.println(format(IMPORT_TEMPLATE, Map.class.getName()));
            out.println();

            out.println("@Component");
            out.println(
                    format(
                            "public class %s extends %s<%s> {",
                            resolverClassName,
                            CountryResolver.class.getSimpleName(),
                            simpleClassName
                    )
            );
            out.println();

            out.println(
                    format(
                            "public %s(%s request, List<%s> services) {",
                            resolverClassName,
                            HttpServletRequest.class.getSimpleName(),
                            simpleClassName
                    )
            );
            out.println("super(request, services);");
            out.println("}");
            out.println();

            List<? extends Element> subElements = classDefinition.getEnclosedElements();
            subElements.stream()
                    .filter(element -> element.getKind().equals(ElementKind.METHOD))
                    .filter(element -> !element.getModifiers().contains(Modifier.PRIVATE))
                    .filter(element -> !element.getModifiers().contains(Modifier.STATIC))
                    .forEach(element -> overrideMethodWithResolver(out, (ExecutableElement) element));

            out.println("}");

        }
    }

    private void overrideMethodWithResolver(PrintWriter out, ExecutableElement element) {
        Set<Modifier> qualifiers = element.getModifiers();
        TypeMirror typeReturn = element.getReturnType();
        Name methodName = element.getSimpleName();
        List<? extends VariableElement> parameters = element.getParameters();

        out.print(qualifiers.stream().map(Modifier::toString).collect(joining(" ")));
        out.print(" ".concat(typeReturn.toString()));
        out.print(" ".concat(methodName.toString()).concat("("));
        out.print(parameters.stream().map(p -> p.asType().toString().concat(" ").concat(p.getSimpleName().toString())).collect(joining(",")));
        out.println(") {");
        if(typeReturn.getKind() != TypeKind.VOID){
            out.print("return ");
        }
        out.print("super.resolve().".concat(methodName.toString()).concat("("));
        out.print(parameters.stream().map(p -> p.getSimpleName().toString()).collect(joining(",")));
        out.println(");");
        out.println("}");
        out.println();
    }

}
