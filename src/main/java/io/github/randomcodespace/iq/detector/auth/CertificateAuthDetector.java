package io.github.randomcodespace.iq.detector.auth;

import io.github.randomcodespace.iq.detector.AbstractRegexDetector;
import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CertificateAuthDetector extends AbstractRegexDetector {

    private record PatternDef(Pattern regex, String authType) {}

    private static final List<PatternDef> MTLS_PATTERNS = List.of(
            new PatternDef(Pattern.compile("\\bssl_verify_client\\b"), "mtls"),
            new PatternDef(Pattern.compile("\\brequestCert\\s*:\\s*true\\b"), "mtls"),
            new PatternDef(Pattern.compile("\\bclientAuth\\s*=\\s*\"true\""), "mtls"),
            new PatternDef(Pattern.compile("\\bX509AuthenticationFilter\\b"), "mtls"),
            new PatternDef(Pattern.compile("\\bAddCertificateForwarding\\b"), "mtls")
    );

    private static final List<PatternDef> X509_PATTERNS = List.of(
            new PatternDef(Pattern.compile("\\bX509AuthenticationFilter\\b"), "x509"),
            new PatternDef(Pattern.compile("\\bCertificateAuthenticationDefaults\\b"), "x509"),
            new PatternDef(Pattern.compile("\\.x509\\s*\\("), "x509")
    );

    private static final List<PatternDef> TLS_CONFIG_PATTERNS = List.of(
            new PatternDef(Pattern.compile("\\bjavax\\.net\\.ssl\\.keyStore\\b"), "tls_config"),
            new PatternDef(Pattern.compile("\\bssl\\.SSLContext\\b"), "tls_config"),
            new PatternDef(Pattern.compile("\\btls\\.createServer\\b"), "tls_config"),
            new PatternDef(Pattern.compile("(?:cert|key|ca)\\s*[=:]\\s*(?:fs\\.readFileSync\\s*\\(|['\"][\\w/.\\\\-]+\\.(?:pem|crt|key|cert)['\"])"), "tls_config"),
            new PatternDef(Pattern.compile("\\btrustStore\\b"), "tls_config")
    );

    private static final List<PatternDef> AZURE_AD_PATTERNS = List.of(
            new PatternDef(Pattern.compile("\\bAzureAd\\b"), "azure_ad"),
            new PatternDef(Pattern.compile("\\bAZURE_TENANT_ID\\b"), "azure_ad"),
            new PatternDef(Pattern.compile("\\bAZURE_CLIENT_ID\\b"), "azure_ad"),
            new PatternDef(Pattern.compile("\\bmsal\\b"), "azure_ad"),
            new PatternDef(Pattern.compile("['\"]@azure/msal-browser['\"]"), "azure_ad"),
            new PatternDef(Pattern.compile("\\bAddMicrosoftIdentityWebApi\\b"), "azure_ad"),
            new PatternDef(Pattern.compile("\\bClientCertificateCredential\\b"), "azure_ad")
    );

    private static final List<PatternDef> ALL_PATTERNS;

    static {
        ALL_PATTERNS = new ArrayList<>();
        ALL_PATTERNS.addAll(MTLS_PATTERNS);
        ALL_PATTERNS.addAll(X509_PATTERNS);
        ALL_PATTERNS.addAll(TLS_CONFIG_PATTERNS);
        ALL_PATTERNS.addAll(AZURE_AD_PATTERNS);
    }

    private static final Pattern CERT_PATH_RE = Pattern.compile("['\"]([^'\"]*\\.(?:pem|crt|key|cert|pfx|p12))['\"]");
    private static final Pattern TENANT_ID_RE = Pattern.compile("AZURE_TENANT_ID\\s*[=:]\\s*['\"]?([a-f0-9-]+)['\"]?");

    @Override
    public String getName() {
        return "certificate_auth";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("java", "python", "typescript", "csharp", "json", "yaml");
    }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        List<CodeNode> nodes = new ArrayList<>();
        String text = ctx.content();
        if (text == null || text.isEmpty()) {
            return DetectorResult.empty();
        }

        String filePath = ctx.filePath();
        String[] lines = text.split("\n", -1);
        Set<Integer> seenLines = new HashSet<>();

        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            String line = lines[lineIdx];
            for (PatternDef pdef : ALL_PATTERNS) {
                if (seenLines.contains(lineIdx)) {
                    break;
                }
                if (pdef.regex.matcher(line).find()) {
                    seenLines.add(lineIdx);
                    int lineNum = lineIdx + 1;
                    String matchedText = line.strip();

                    CodeNode node = new CodeNode();
                    node.setId("auth:" + filePath + ":cert:" + lineNum);
                    node.setKind(NodeKind.GUARD);
                    String truncLabel = matchedText.length() > 60 ? matchedText.substring(0, 60) : matchedText;
                    node.setLabel("Certificate auth (" + pdef.authType + "): " + truncLabel);
                    node.setFilePath(filePath);
                    node.setLineStart(lineNum);
                    node.setLineEnd(lineNum);
                    node.getProperties().put("auth_type", pdef.authType);
                    node.getProperties().put("language", ctx.language());
                    String truncPattern = matchedText.length() > 120 ? matchedText.substring(0, 120) : matchedText;
                    node.getProperties().put("pattern", truncPattern);

                    Matcher certM = CERT_PATH_RE.matcher(line);
                    if (certM.find()) {
                        node.getProperties().put("cert_path", certM.group(1));
                    }

                    Matcher tenantM = TENANT_ID_RE.matcher(line);
                    if (tenantM.find()) {
                        node.getProperties().put("tenant_id", tenantM.group(1));
                    }

                    if ("azure_ad".equals(pdef.authType)) {
                        if (line.contains("ClientCertificateCredential")) {
                            node.getProperties().put("auth_flow", "client_certificate");
                        } else if (line.toLowerCase().contains("msal")) {
                            node.getProperties().put("auth_flow", "msal");
                        }
                    }

                    nodes.add(node);
                }
            }
        }

        return DetectorResult.of(nodes, List.of());
    }
}
