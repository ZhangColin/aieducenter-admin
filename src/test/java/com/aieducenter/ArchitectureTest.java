package com.aieducenter;

import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.Port;
import com.cartisan.test.archunit.CartisanLayeringRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import org.springframework.stereotype.Component;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * 架构守护测试。
 *
 * <p>继承 cartisan-boot 框架的分层规则（4条）。</p>
 *
 * <h2>框架已更新（2026-04-05）</h2>
 * <p>框架已修复 14 次违规：</p>
 * <ul>
 *   <li>Repository 接口可使用 {@code @Query}、{@code @Param}（规则排除接口）</li>
 *   <li>Controller 可使用领域枚举（框架支持 BaseEnum 参数绑定）</li>
 *   <li>Controller 可使用 Pageable（Spring Data 标准用法）</li>
 * </ul>
 *
 * <h2>项目特定例外</h2>
 * <p><b>AdminUser 聚合根使用 BCryptPasswordEncoder</b></p>
 *
 * <p><b>理由</b>：密码编码是领域层核心职责，{@code AdminUser.matchesPassword()} 是领域逻辑</p>
 * <p><b>依据</b>：项目规范 DDD-005 明确许可；cartisan-boot 使用手册提到 AdminUser 例外</p>
 *
 * @see com.cartisan.test.archunit.CartisanLayeringRules
 */
@AnalyzeClasses(packages = "com.aieducenter", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest extends CartisanLayeringRules {

    /**
     * BFF 出站服务客户端为裸 {@code @Component}（ADR-0007）：infrastructure 内以 {@code "Client"}
     * 结尾的类必须是 {@code @Component}、不得是 {@code @Port}/{@code @Adapter}。
     *
     * <p>{@code @Port}/{@code @Adapter} 是<strong>业务服务</strong>南向端口的 DDD 规范（Domain 定义 Port、
     * Infra 实现 Adapter，如 {@code PasswordEncoder}）；admin 是 BFF（调接口 + DTO 转换 + 聚合），
     * 出站 HTTP 客户端（{@code PaymentClient} / {@code AppRegistryClient}）为裸 {@code @Component}、
     * 应用层直接注入。匹配 {@code "Client"} 后缀以避开 {@code BCryptPasswordEncoderAdapter} 等真端口适配器。</p>
     */
    @ArchTest
    static final ArchRule outboundServiceClientsShouldBeComponents =
            classes().that().resideInAPackage("..infrastructure..")
                    .and().haveSimpleNameEndingWith("Client")
                    .should().beAnnotatedWith(Component.class)
                    .andShould().notBeAnnotatedWith(Port.class)
                    .andShould().notBeAnnotatedWith(Adapter.class)
                    .because("BFF 出站服务客户端（PaymentClient / AppRegistryClient）为裸 @Component，"
                            + "@Port/@Adapter 留给真正的业务南向端口（如 PasswordEncoder）；见 ADR-0007。");
}
