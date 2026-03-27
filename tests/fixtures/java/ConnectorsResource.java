package org.apache.kafka.connect.runtime.rest.resources;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/connectors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConnectorsResource {

    @GET
    public List<String> listConnectors() {
        return null;
    }

    @POST
    public Response createConnector(CreateConnectorRequest request) {
        return null;
    }

    @GET
    @Path("/{connector}")
    public ConnectorInfo getConnector(@PathParam("connector") String connector) {
        return null;
    }

    @DELETE
    @Path("/{connector}")
    public void destroyConnector(@PathParam("connector") String connector) {
    }
}
