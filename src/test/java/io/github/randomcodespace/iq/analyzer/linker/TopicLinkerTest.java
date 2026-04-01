package io.github.randomcodespace.iq.analyzer.linker;

import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TopicLinkerTest {

    private final TopicLinker linker = new TopicLinker();

    @Test
    void linksProducerToConsumerViaTopic() {
        var topic = new CodeNode("topic:orders", NodeKind.TOPIC, "orders");
        var producer = new CodeNode("svc:OrderService", NodeKind.CLASS, "OrderService");
        var consumer = new CodeNode("svc:PaymentService", NodeKind.CLASS, "PaymentService");

        var producesEdge = new CodeEdge();
        producesEdge.setId("e1");
        producesEdge.setKind(EdgeKind.PRODUCES);
        producesEdge.setSourceId("svc:OrderService");
        producesEdge.setTarget(topic);

        var consumesEdge = new CodeEdge();
        consumesEdge.setId("e2");
        consumesEdge.setKind(EdgeKind.CONSUMES);
        consumesEdge.setSourceId("svc:PaymentService");
        consumesEdge.setTarget(topic);

        LinkResult result = linker.link(
                List.of(topic, producer, consumer),
                List.of(producesEdge, consumesEdge)
        );

        assertEquals(1, result.edges().size());
        CodeEdge callsEdge = result.edges().getFirst();
        assertEquals(EdgeKind.CALLS, callsEdge.getKind());
        assertEquals("svc:OrderService", callsEdge.getSourceId());
        assertEquals("svc:PaymentService", callsEdge.getTarget().getId());
        assertEquals(true, callsEdge.getProperties().get("inferred"));
        assertEquals("orders", callsEdge.getProperties().get("topic"));
    }

    @Test
    void doesNotLinkProducerToItself() {
        var topic = new CodeNode("topic:self", NodeKind.TOPIC, "self");
        var svc = new CodeNode("svc:SelfService", NodeKind.CLASS, "SelfService");

        var producesEdge = new CodeEdge();
        producesEdge.setId("e1");
        producesEdge.setKind(EdgeKind.PRODUCES);
        producesEdge.setSourceId("svc:SelfService");
        producesEdge.setTarget(topic);

        var consumesEdge = new CodeEdge();
        consumesEdge.setId("e2");
        consumesEdge.setKind(EdgeKind.CONSUMES);
        consumesEdge.setSourceId("svc:SelfService");
        consumesEdge.setTarget(topic);

        LinkResult result = linker.link(List.of(topic, svc), List.of(producesEdge, consumesEdge));

        assertTrue(result.edges().isEmpty());
    }

    @Test
    void noTopicsReturnsEmpty() {
        var node = new CodeNode("svc:A", NodeKind.CLASS, "A");
        LinkResult result = linker.link(List.of(node), List.of());

        assertTrue(result.edges().isEmpty());
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void linksSendsToWithReceivesFrom() {
        var topic = new CodeNode("topic:events", NodeKind.TOPIC, "events");
        var sender = new CodeNode("svc:TibcoSender", NodeKind.CLASS, "TibcoSender");
        var receiver = new CodeNode("svc:TibcoReceiver", NodeKind.CLASS, "TibcoReceiver");

        var sendsEdge = new CodeEdge();
        sendsEdge.setId("e1");
        sendsEdge.setKind(EdgeKind.SENDS_TO);
        sendsEdge.setSourceId("svc:TibcoSender");
        sendsEdge.setTarget(topic);

        var receivesEdge = new CodeEdge();
        receivesEdge.setId("e2");
        receivesEdge.setKind(EdgeKind.RECEIVES_FROM);
        receivesEdge.setSourceId("svc:TibcoReceiver");
        receivesEdge.setTarget(topic);

        LinkResult result = linker.link(
                List.of(topic, sender, receiver),
                List.of(sendsEdge, receivesEdge)
        );

        assertEquals(1, result.edges().size());
        CodeEdge callsEdge = result.edges().getFirst();
        assertEquals(EdgeKind.CALLS, callsEdge.getKind());
        assertEquals("svc:TibcoSender", callsEdge.getSourceId());
        assertEquals("svc:TibcoReceiver", callsEdge.getTarget().getId());
        assertEquals("events", callsEdge.getProperties().get("topic"));
    }

    @Test
    void linksMixedSendsToAndConsumes() {
        var topic = new CodeNode("topic:orders", NodeKind.TOPIC, "orders");
        var sender = new CodeNode("svc:AzureSender", NodeKind.CLASS, "AzureSender");
        var consumer = new CodeNode("svc:OrderWorker", NodeKind.CLASS, "OrderWorker");

        var sendsEdge = new CodeEdge();
        sendsEdge.setId("e1");
        sendsEdge.setKind(EdgeKind.SENDS_TO);
        sendsEdge.setSourceId("svc:AzureSender");
        sendsEdge.setTarget(topic);

        var consumesEdge = new CodeEdge();
        consumesEdge.setId("e2");
        consumesEdge.setKind(EdgeKind.CONSUMES);
        consumesEdge.setSourceId("svc:OrderWorker");
        consumesEdge.setTarget(topic);

        LinkResult result = linker.link(
                List.of(topic, sender, consumer),
                List.of(sendsEdge, consumesEdge)
        );

        assertEquals(1, result.edges().size());
        assertEquals(EdgeKind.CALLS, result.edges().getFirst().getKind());
    }

    @Test
    void handlesQueueNodes() {
        var queue = new CodeNode("queue:tasks", NodeKind.QUEUE, "tasks");
        var producer = new CodeNode("svc:TaskCreator", NodeKind.CLASS, "TaskCreator");
        var consumer = new CodeNode("svc:TaskWorker", NodeKind.CLASS, "TaskWorker");

        var producesEdge = new CodeEdge();
        producesEdge.setId("e1");
        producesEdge.setKind(EdgeKind.PRODUCES);
        producesEdge.setSourceId("svc:TaskCreator");
        producesEdge.setTarget(queue);

        var consumesEdge = new CodeEdge();
        consumesEdge.setId("e2");
        consumesEdge.setKind(EdgeKind.CONSUMES);
        consumesEdge.setSourceId("svc:TaskWorker");
        consumesEdge.setTarget(queue);

        LinkResult result = linker.link(
                List.of(queue, producer, consumer),
                List.of(producesEdge, consumesEdge)
        );

        assertEquals(1, result.edges().size());
    }
}
