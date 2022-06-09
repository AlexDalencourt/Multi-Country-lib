package com.multi.country.lib.core.processors;

import com.google.auto.service.AutoService;
import com.multi.country.lib.core.annotations.CountryFieldBinder;
import org.reflections.Reflections;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static javax.tools.Diagnostic.Kind.ERROR;

@SupportedAnnotationTypes("com.multi.country.lib.core.annotations.CountryFieldBinder")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class CountryFieldBinderProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            List<VariableElement> annotatedElements = new ArrayList<>(roundEnv.getElementsAnnotatedWith(annotation)).stream().map(element -> (VariableElement) element).toList();

            Map<TypeElement, List<VariableElement>> sortedFields = annotatedElements.stream()
                    .collect(Collectors.groupingBy(element -> (TypeElement) element.getEnclosingElement()));

            for (Map.Entry<TypeElement, List<VariableElement>> entry : sortedFields.entrySet()) {
                try {
                    writeFactoryFile(entry.getKey(), entry.getValue());
                } catch (IOException | NoSuchMethodException e) {
                    processingEnv.getMessager().printMessage(ERROR, e.getMessage());
                    return false;
                }
            }
        }
        return true;
    }

    private void writeFactoryFile(TypeElement typeElement, List<VariableElement> fields) throws IOException, NoSuchMethodException {

        String className = typeElement.getQualifiedName().toString();
        String packageName = null;
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = className.substring(0, lastDot);
        }

        String simpleClassName = className.substring(lastDot + 1);
        String resolverClassName = simpleClassName + "Binder";

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(packageName + "." + resolverClassName);
        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {

            if (packageName != null) {
                out.print("package ");
                out.print(packageName);
                out.println(";");
                out.println();
            }

            out.println(
                    format(
                            "public class %s {",
                            resolverClassName
                    )
            );
            out.println();

            for (VariableElement currentField : fields) {
                writeFieldBinder(currentField, out);
            }

            out.println("}");
        }

    }

    private void writeFieldBinder(VariableElement currentField, PrintWriter out) {


        CountryFieldBinder annotationInformations = currentField.getAnnotation(CountryFieldBinder.class);

        String fieldName = currentField.getSimpleName() + "Binder";
        TypeMirror typeOfField = currentField.asType();
        List<? extends TypeMirror> typeParams = getTypeMirrors(annotationInformations, CountryFieldBinder::constructorParameters);
        TypeMirror typeBinder =  getTypeMirror(annotationInformations, CountryFieldBinder::countryParameterizedClass);
        if(typeParams == null || typeBinder == null){
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "CountryFieldBinder: constructorParameters or countryParameterizedClass are not set", currentField);
        }

        TypeElement mainTypeBinder = (TypeElement)processingEnv.getTypeUtils().asElement(typeBinder);
        List<TypeElement> subTypesBinder = resolveSubTypes(mainTypeBinder);
//        mainTypeBinder.getAnnotationMirrors().stream().filter(annotation -> annotation.)

        out.println(format("// public static final java.util.function.BiFunction<%s,%s,%s> %s = (u,b) -> null;", typeParams, typeBinder, typeOfField, fieldName));
    }

    private List<TypeElement> resolveSubTypes(TypeElement mainTypeBinder) {
        String className = mainTypeBinder.getQualifiedName().toString();
        String packageName = null;
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = className.substring(0, lastDot);
        }
        Reflections reflector = new Reflections(packageName);
        try {
            Set<Class<?>> subClasses = reflector.getSubTypesOf((Class<Object>) Class.forName(className));
            return subClasses.stream().map(
                    clazz -> processingEnv.getElementUtils().getTypeElement(clazz.getCanonicalName())
            ).toList();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private List<? extends TypeMirror> getTypeMirrors(CountryFieldBinder annotationInformations, Function<CountryFieldBinder, Class<?>[]> getter) {
        try {
            annotationInformations.constructorParameters();
        } catch (MirroredTypesException e) {
            return e.getTypeMirrors();
        }
        return null;
    }

    private TypeMirror getTypeMirror(CountryFieldBinder annotationInformations, Function<CountryFieldBinder, Class<?>> getter) {
        try {
            getter.apply(annotationInformations);
        } catch (MirroredTypeException e) {
            return e.getTypeMirror();
        }
        return null;
    }


}
