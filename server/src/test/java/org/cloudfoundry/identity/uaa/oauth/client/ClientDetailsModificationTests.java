/*
 * *****************************************************************************
 *      Cloud Foundry
 *      Copyright (c) [2009-2015] Pivotal Software, Inc. All Rights Reserved.
 *      This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *      You may not use this product except in compliance with the License.
 *
 *      This product includes a number of subcomponents with
 *      separate copyright notices and license terms. Your use of these
 *      subcomponents is subject to the terms and conditions of the
 *      subcomponent's license, as noted in the LICENSE file.
 * *****************************************************************************
 */

package org.cloudfoundry.identity.uaa.oauth.client;

import org.cloudfoundry.identity.uaa.util.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by fhanik on 5/5/15.
 */
public class ClientDetailsModificationTests {

    @Test
    public void testClientDetailsModificationDeserialize() {
        String data = """
                {"scope":
                        ["bar","foo","oauth.approvals"],
                        "client_id":"Kn30XB",
                        "resource_ids":["none"],
                        "authorized_grant_types":["password","refresh_token"],
                        "autoapprove":[],
                        "action":"none",
                        "approvals_deleted":true,
                        "authorities":["uaa.none"],
                        "action":"none",
                        "foo":["bar"],
                        "lastModified":1430849491767
                    }\
                """;

        ClientDetailsModification details = JsonUtils.readValue(data, ClientDetailsModification.class);
        assertTrue(details.isApprovalsDeleted());

    }
}
