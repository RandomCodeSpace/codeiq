"""Tests for Angular component, service, directive, pipe, and module detector."""

from osscodeiq.detectors.base import DetectorContext
from osscodeiq.detectors.frontend.angular_components import AngularComponentDetector
from osscodeiq.models.graph import NodeKind


def _ctx(content: str, file_path: str = "src/app/app.component.ts") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language="typescript",
        content=content.encode("utf-8"),
        module_name="test-module",
    )


class TestComponentDecorator:
    def test_basic_component(self):
        source = """\
import { Component } from '@angular/core';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent {
  title = 'Dashboard';
}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source, "src/app/dashboard.component.ts"))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "DashboardComponent"
        assert components[0].properties["framework"] == "angular"
        assert components[0].properties["selector"] == "app-dashboard"
        assert components[0].properties["decorator"] == "Component"
        assert components[0].id == "angular:src/app/dashboard.component.ts:component:DashboardComponent"

    def test_component_with_inline_template(self):
        source = """\
@Component({
  selector: 'app-header',
  template: '<h1>{{ title }}</h1>',
})
class HeaderComponent {
  title = 'Header';
}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "HeaderComponent"
        assert components[0].properties["selector"] == "app-header"

    def test_component_double_quote_selector(self):
        source = """\
@Component({
  selector: "app-footer",
  template: '',
})
export class FooterComponent {}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].properties["selector"] == "app-footer"


class TestInjectableDecorator:
    def test_basic_injectable(self):
        source = """\
import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  isLoggedIn = false;
}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source, "src/app/auth.service.ts"))
        services = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(services) == 1
        assert services[0].label == "AuthService"
        assert services[0].properties["framework"] == "angular"
        assert services[0].properties["provided_in"] == "root"
        assert services[0].properties["decorator"] == "Injectable"
        assert services[0].id == "angular:src/app/auth.service.ts:service:AuthService"

    def test_injectable_no_export(self):
        source = """\
@Injectable({
  providedIn: 'root',
})
class DataService {
  getData() { return []; }
}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source))
        services = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(services) == 1
        assert services[0].label == "DataService"


class TestDirectiveDecorator:
    def test_basic_directive(self):
        source = """\
import { Directive, ElementRef } from '@angular/core';

@Directive({
  selector: '[appHighlight]',
})
export class HighlightDirective {
  constructor(private el: ElementRef) {}
}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source, "src/app/highlight.directive.ts"))
        directives = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(directives) == 1
        assert directives[0].label == "HighlightDirective"
        assert directives[0].properties["selector"] == "[appHighlight]"
        assert directives[0].properties["decorator"] == "Directive"

    def test_attribute_directive(self):
        source = """\
@Directive({
  selector: '[appTooltip]',
})
export class TooltipDirective {}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].properties["selector"] == "[appTooltip]"


class TestPipeDecorator:
    def test_basic_pipe(self):
        source = """\
import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'truncate',
})
export class TruncatePipe implements PipeTransform {
  transform(value: string, limit: number): string {
    return value.substring(0, limit);
  }
}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source, "src/app/truncate.pipe.ts"))
        pipes = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(pipes) == 1
        assert pipes[0].label == "TruncatePipe"
        assert pipes[0].properties["pipe_name"] == "truncate"
        assert pipes[0].properties["decorator"] == "Pipe"

    def test_pipe_double_quotes(self):
        source = """\
@Pipe({
  name: "dateFormat",
})
export class DateFormatPipe {}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source))
        pipes = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(pipes) == 1
        assert pipes[0].properties["pipe_name"] == "dateFormat"


class TestNgModuleDecorator:
    def test_basic_ngmodule(self):
        source = """\
import { NgModule } from '@angular/core';

@NgModule({
  declarations: [AppComponent, HeaderComponent],
  imports: [BrowserModule],
  bootstrap: [AppComponent],
})
export class AppModule {}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source, "src/app/app.module.ts"))
        modules = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(modules) == 1
        assert modules[0].label == "AppModule"
        assert modules[0].properties["decorator"] == "NgModule"

    def test_feature_module(self):
        source = """\
@NgModule({
  declarations: [UserListComponent, UserDetailComponent],
  imports: [CommonModule, RouterModule],
})
export class UserModule {}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source))
        modules = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(modules) == 1
        assert modules[0].label == "UserModule"


class TestMultipleDecorators:
    def test_file_with_mixed_decorators(self):
        source = """\
import { Component, Pipe, PipeTransform } from '@angular/core';

@Component({
  selector: 'app-widget',
  template: '<p>{{ data | myPipe }}</p>',
})
export class WidgetComponent {
  data = 'hello';
}

@Pipe({
  name: 'myPipe',
})
export class MyPipe implements PipeTransform {
  transform(value: string): string {
    return value.toUpperCase();
  }
}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 2
        labels = {c.label for c in components}
        assert "WidgetComponent" in labels
        assert "MyPipe" in labels


class TestStatelessAndDeterministic:
    def test_deterministic(self):
        source = """\
@Component({
  selector: 'app-test',
})
export class TestComponent {}
"""
        detector = AngularComponentDetector()
        r1 = detector.detect(_ctx(source))
        r2 = detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        for n1, n2 in zip(r1.nodes, r2.nodes):
            assert n1.id == n2.id
            assert n1.kind == n2.kind

    def test_stateless(self):
        source1 = """\
@Component({ selector: 'app-foo' })
export class FooComponent {}
"""
        source2 = """\
@Component({ selector: 'app-bar' })
export class BarComponent {}
"""
        detector = AngularComponentDetector()
        r1 = detector.detect(_ctx(source1))
        r2 = detector.detect(_ctx(source2))
        assert r1.nodes[0].label == "FooComponent"
        assert r2.nodes[0].label == "BarComponent"


class TestEdgeCases:
    def test_empty_file(self):
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(""))
        assert result.nodes == []

    def test_no_decorators(self):
        source = """\
export class PlainClass {
  doStuff() {}
}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source))
        assert result.nodes == []

    def test_line_numbers_accurate(self):
        source = """\
// line 1
// line 2
@Component({
  selector: 'app-line-test',
})
export class LineTestComponent {}
"""
        detector = AngularComponentDetector()
        result = detector.detect(_ctx(source))
        assert result.nodes[0].location.line_start == 3

    def test_only_typescript_supported(self):
        detector = AngularComponentDetector()
        assert detector.supported_languages == ("typescript",)
