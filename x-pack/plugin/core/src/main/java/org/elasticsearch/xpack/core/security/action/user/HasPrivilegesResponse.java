/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.security.action.user;

import org.elasticsearch.Version;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Response for a {@link HasPrivilegesRequest}
 */
public class HasPrivilegesResponse extends ActionResponse implements ToXContentObject {
    private String username;
    private boolean completeMatch;
    private Map<String, Boolean> cluster;
    private List<ResourcePrivileges> index;
    private Map<String, List<ResourcePrivileges>> application;

    public HasPrivilegesResponse() {
        this("", true, Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap());
    }

    public HasPrivilegesResponse(String username, boolean completeMatch, Map<String, Boolean> cluster, Collection<ResourcePrivileges> index,
                                 Map<String, Collection<ResourcePrivileges>> application) {
        super();
        this.username = username;
        this.completeMatch = completeMatch;
        this.cluster = new HashMap<>(cluster);
        this.index = sorted(new ArrayList<>(index));
        this.application = new HashMap<>();
        application.forEach((key, val) -> this.application.put(key, Collections.unmodifiableList(sorted(new ArrayList<>(val)))));
    }

    private static List<ResourcePrivileges> sorted(List<ResourcePrivileges> resources) {
        Collections.sort(resources, Comparator.comparing(o -> o.resource));
        return resources;
    }

    public String getUsername() {
        return username;
    }

    public boolean isCompleteMatch() {
        return completeMatch;
    }

    public Map<String, Boolean> getClusterPrivileges() {
        return Collections.unmodifiableMap(cluster);
    }

    public List<ResourcePrivileges> getIndexPrivileges() {
        return Collections.unmodifiableList(index);
    }

    /**
     * Retrieves the results from checking application privileges,
     * @return A {@code Map} keyed by application-name
     */
    public Map<String, List<ResourcePrivileges>> getApplicationPrivileges() {
        return Collections.unmodifiableMap(application);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HasPrivilegesResponse response = (HasPrivilegesResponse) o;
        return completeMatch == response.completeMatch
            && Objects.equals(username, response.username)
            && Objects.equals(cluster, response.cluster)
            && Objects.equals(index, response.index)
            && Objects.equals(application, response.application);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, completeMatch, cluster, index, application);
    }

    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        completeMatch = in.readBoolean();
        if (in.getVersion().onOrAfter(Version.V_6_6_0 )) {
            cluster = in.readMap(StreamInput::readString, StreamInput::readBoolean);
        }
        index = readResourcePrivileges(in);
        if (in.getVersion().onOrAfter(Version.V_6_4_0)) {
            application = in.readMap(StreamInput::readString, HasPrivilegesResponse::readResourcePrivileges);
        }
        if (in.getVersion().onOrAfter(Version.V_6_6_0)) {
            username = in.readString();
        }
    }

    private static List<ResourcePrivileges> readResourcePrivileges(StreamInput in) throws IOException {
        final int count = in.readVInt();
        final List<ResourcePrivileges> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final String index = in.readString();
            final Map<String, Boolean> privileges = in.readMap(StreamInput::readString, StreamInput::readBoolean);
            list.add(new ResourcePrivileges(index, privileges));
        }
        return list;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeBoolean(completeMatch);
        if (out.getVersion().onOrAfter(Version.V_6_6_0)) {
            out.writeMap(cluster, StreamOutput::writeString, StreamOutput::writeBoolean);
        }
        writeResourcePrivileges(out, index);
        if (out.getVersion().onOrAfter(Version.V_6_4_0)) {
            out.writeMap(application, StreamOutput::writeString, HasPrivilegesResponse::writeResourcePrivileges);
        }
        if (out.getVersion().onOrAfter(Version.V_6_6_0)) {
            out.writeString(username);
        }
    }

    private static void writeResourcePrivileges(StreamOutput out, List<ResourcePrivileges> privileges) throws IOException {
        out.writeVInt(privileges.size());
        for (ResourcePrivileges priv : privileges) {
            out.writeString(priv.resource);
            out.writeMap(priv.privileges, StreamOutput::writeString, StreamOutput::writeBoolean);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{"
            + "username=" + username + ","
            + "completeMatch=" + completeMatch + ","
            + "cluster=" + cluster + ","
            + "index=" + index + ","
            + "application=" + application
            + "}";
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject()
            .field("username", username)
            .field("has_all_requested", completeMatch);

        builder.field("cluster");
        builder.map(cluster);

        appendResources(builder, "index", index);

        builder.startObject("application");
        for (String app : application.keySet()) {
            appendResources(builder, app, application.get(app));
        }
        builder.endObject();

        builder.endObject();
        return builder;
    }

    private void appendResources(XContentBuilder builder, String field, List<HasPrivilegesResponse.ResourcePrivileges> privileges)
        throws IOException {
        builder.startObject(field);
        for (HasPrivilegesResponse.ResourcePrivileges privilege : privileges) {
            builder.field(privilege.getResource());
            builder.map(privilege.getPrivileges());
        }
        builder.endObject();
    }


    public static class ResourcePrivileges {
        private final String resource;
        private final Map<String, Boolean> privileges;

        public ResourcePrivileges(String resource, Map<String, Boolean> privileges) {
            this.resource = Objects.requireNonNull(resource);
            this.privileges = Collections.unmodifiableMap(privileges);
        }

        public String getResource() {
            return resource;
        }

        public Map<String, Boolean> getPrivileges() {
            return privileges;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{" +
                    "resource='" + resource + '\'' +
                    ", privileges=" + privileges +
                    '}';
        }

        @Override
        public int hashCode() {
            int result = resource.hashCode();
            result = 31 * result + privileges.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final ResourcePrivileges other = (ResourcePrivileges) o;
            return this.resource.equals(other.resource) && this.privileges.equals(other.privileges);
        }
    }
}
