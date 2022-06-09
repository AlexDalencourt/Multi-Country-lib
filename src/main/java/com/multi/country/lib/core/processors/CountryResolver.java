package com.multi.country.lib.core.processors;

import com.multi.country.lib.core.annotations.Country;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.core.annotation.AnnotatedElementUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CountryResolver<T> {

    private static final String DEFAULT_COUNTRY_CODE = "all";

    private final Map<String, T> beansByCountry;

    private final HttpServletRequest request;

    public CountryResolver(HttpServletRequest request, List<T> services) {
        this.request = request;
        beansByCountry = services.stream().collect(Collectors.toMap(
                service ->
                        Optional.ofNullable(AnnotatedElementUtils.getMergedAnnotation(service.getClass(), Country.class))
                                .map(Country::value).orElse(DEFAULT_COUNTRY_CODE),
                service -> service,
                (bean1, bean2) -> {throw new NoUniqueBeanDefinitionException(bean1.getClass(), bean1.toString(), bean2.toString());}
        ));
    }

    protected T resolve() {
        return beansByCountry.getOrDefault(request.getHeader("X-TenantID"), beansByCountry.get(DEFAULT_COUNTRY_CODE));
    }
}
