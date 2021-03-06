/**
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * <p>
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.esb.message.processor.test.forwarding;

import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.commons.io.FileUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.test.utils.dbutils.H2DataBaseManager;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.Utils;
import org.wso2.esb.integration.common.utils.clients.axis2client.AxisServiceClient;
import org.wso2.esb.integration.common.utils.common.XPathConstants;

import java.io.File;
import java.util.Random;

/**
 * Testing whether the message processor is consuming the message from the JDBC store
 * when the message has no body.
 * Issue : https://github.com/wso2/product-ei/issues/1449
 */
public class StoreAndForwardWithEmptyMessageBodyTesCase extends ESBIntegrationTest {

    private H2DataBaseManager h2DatabaseManager;
    private static CarbonLogReader carbonLogReader;

    private String JDBC_URL;

    @BeforeClass
    public void init() throws Exception {
        super.init();
        AutomationContext automationContext = new AutomationContext();
        String dbPassword = automationContext.getConfigurationValue(XPathConstants.DATA_SOURCE_DB_PASSWORD);
        JDBC_URL = automationContext.getConfigurationValue(XPathConstants.DATA_SOURCE_URL);
        String dbUser = automationContext.getConfigurationValue(XPathConstants.DATA_SOURCE_DB_USER_NAME);
        String databaseName =
                System.getProperty("basedir") + File.separator + "target" + File.separator + "testdb_store"
                        + new Random().nextInt();
        JDBC_URL = JDBC_URL + databaseName + ";DB_CLOSE_ON_EXIT=FALSE;AUTO_SERVER=TRUE";
        h2DatabaseManager = new H2DataBaseManager(JDBC_URL, dbUser, dbPassword);
        h2DatabaseManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS JDBC_MESSAGE_STORE(\n" + "indexId BIGINT(20) NOT NULL AUTO_INCREMENT,\n"
                        + "msg_id VARCHAR(200) NOT NULL ,\n" + "message BLOB NOT NULL,\n" + "PRIMARY KEY ( indexId )\n"
                        + ")");
        carbonLogReader = new CarbonLogReader();
    }

    @Test
    public void testWithEmptyMessage() throws Exception {

        String location = getESBResourceLocation() + File.separator + "messageProcessorConfig" + File.separator
                + "EmptyMsgBodyMessageStoreTest.xml";
        String proxyContent = FileUtils.readFileToString(new File(location));
        proxyContent = this.updateDatabaseInfo(proxyContent);

        String artifactName = "EmptyMsgBodyMessageStoreTest";
        String artifactType = "message-stores";
        Utils.deploySynapseConfiguration(AXIOMUtil.stringToOM(proxyContent), artifactName, artifactType, true);
        String proxyServiceUrl = getProxyServiceURLHttp("EmptyMsgBodyTestProxy");
        AxisServiceClient client = new AxisServiceClient();
        carbonLogReader.start();
        client.fireAndForget(null, proxyServiceUrl, "");
        Assert.assertTrue(Utils.logExists(carbonLogReader, "REPLY = MESSAGE", 10),
                "Message with empty body not processed!");
    }

    @AfterClass
    public void cleanup() throws Exception {
        h2DatabaseManager.disconnect();
        h2DatabaseManager = null;
        super.cleanup();
    }

    private String updateDatabaseInfo(String synapseConfig) {
        synapseConfig = synapseConfig.replace("$url", JDBC_URL);
        return synapseConfig;
    }
}