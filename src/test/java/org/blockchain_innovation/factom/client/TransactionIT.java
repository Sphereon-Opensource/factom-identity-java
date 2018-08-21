package org.blockchain_innovation.factom.client;

import org.blockchain_innovation.factom.client.data.FactomException;
import org.blockchain_innovation.factom.client.data.model.response.factomd.EntryCreditRateResponse;
import org.blockchain_innovation.factom.client.data.model.response.factomd.FactoidSubmitResponse;
import org.blockchain_innovation.factom.client.data.model.response.walletd.AddressResponse;
import org.blockchain_innovation.factom.client.data.model.response.walletd.ComposeTransactionResponse;
import org.blockchain_innovation.factom.client.data.model.response.walletd.ExecutedTransactionResponse;
import org.blockchain_innovation.factom.client.data.model.response.walletd.TransactionResponse;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TransactionIT extends AbstractClientTest {

    private final static String TRANSACTION_NAME = "TransactionName" + System.currentTimeMillis();
    private static FactomResponse<EntryCreditRateResponse> entryCreditRateResponse;
    private static FactomResponse<TransactionResponse> newTransactionResponse;
    private static FactomResponse<AddressResponse> toAddressResponse;
    private static FactomResponse<ComposeTransactionResponse> composeTransactionResponse;


    @Test
    public void _01_getExchangeRate() throws FactomException.ClientException {
        entryCreditRateResponse = factomdClient.entryCreditRate();
        assertValidResponse(entryCreditRateResponse);

        Assert.assertTrue(entryCreditRateResponse.getResult().getRate() > 0);
    }

    @Test
    public void _02_newTransaction() throws FactomException.ClientException {
        newTransactionResponse = walletdClient.newTransaction(TRANSACTION_NAME);
        assertValidResponse(newTransactionResponse);

        Assert.assertNotNull(newTransactionResponse.getResult().getTxId());
        Assert.assertEquals(TRANSACTION_NAME, newTransactionResponse.getResult().getName());
        Assert.assertEquals(0, newTransactionResponse.getResult().getTotalEntryCreditOutputs());
        Assert.assertEquals(0, newTransactionResponse.getResult().getTotalInputs());
        Assert.assertEquals(0, newTransactionResponse.getResult().getTotalOutputs());
        Assert.assertTrue(newTransactionResponse.getResult().getInputs() == null || newTransactionResponse.getResult().getInputs().isEmpty());
        Assert.assertTrue(newTransactionResponse.getResult().getOutputs() == null || newTransactionResponse.getResult().getOutputs().isEmpty());
        Assert.assertTrue(newTransactionResponse.getResult().getEntryCreditOutputs() == null || newTransactionResponse.getResult().getEntryCreditOutputs().isEmpty());
    }

    @Test
    public void _03_newToAddress() throws FactomException.ClientException {
        toAddressResponse = walletdClient.generateEntryCreditAddress();
        assertValidResponse(toAddressResponse);

        Assert.assertNotNull(toAddressResponse.getResult().getPublicAddress());
        Assert.assertNotNull(toAddressResponse.getResult().getSecret());
    }

    @Test
    public void _04_addInput() throws FactomException.ClientException {
        long fctCost = calculateCost();

        FactomResponse<ExecutedTransactionResponse> response = walletdClient.addInput(TRANSACTION_NAME, FACTOID_PUBLIC_KEY, fctCost);
        assertValidResponse(response);

        Assert.assertFalse(response.getResult().getInputs().isEmpty());
        Assert.assertEquals(FACTOID_PUBLIC_KEY, response.getResult().getInputs().get(0).getAddress());
        Assert.assertEquals(fctCost, response.getResult().getInputs().get(0).getAmount());
    }

    @Test
    public void _05_addEntryCreditOutput() throws FactomException.ClientException {
        String toAddress = toAddressResponse.getResult().getPublicAddress();
        long fctCost = calculateCost();

        FactomResponse<TransactionResponse> response = walletdClient.addEntryCreditOutput(TRANSACTION_NAME, toAddress, fctCost);
        assertValidResponse(response);

        Assert.assertFalse(response.getResult().getEntryCreditOutputs().isEmpty());
        Assert.assertEquals(toAddress, response.getResult().getEntryCreditOutputs().get(0).getAddress());
        Assert.assertEquals(fctCost, response.getResult().getEntryCreditOutputs().get(0).getAmount());
    }

    @Test
    public void _06_addFee() throws FactomException.ClientException {
        FactomResponse<ExecutedTransactionResponse> response = walletdClient.addFee(TRANSACTION_NAME, FACTOID_PUBLIC_KEY);
        assertValidResponse(response);

        Assert.assertNotNull(response.getResult().getInputs().isEmpty());
    }

    @Test
    public void _07_signTransaction() throws FactomException.ClientException {
        FactomResponse<ExecutedTransactionResponse> response = walletdClient.signTransaction(TRANSACTION_NAME);
        assertValidResponse(response);

        Assert.assertTrue(response.getResult().isSigned());
    }

    @Test
    public void _08_composeTransaction() throws FactomException.ClientException {
        composeTransactionResponse = walletdClient.composeTransaction(TRANSACTION_NAME);
        assertValidResponse(composeTransactionResponse);

        Assert.assertNotNull(composeTransactionResponse.getResult().getParams().getTransaction());
    }

    @Test
    public void _09_submitTransaction() throws FactomException.ClientException {
        String transaction = composeTransactionResponse.getResult().getParams().getTransaction();
        FactomResponse<FactoidSubmitResponse> response = factomdClient.factoidSubmit(transaction);
        assertValidResponse(response);

        Assert.assertNotNull(response.getResult().getTxId());
        Assert.assertEquals("Successfully submitted the transaction", response.getResult().getMessage());
    }

    private long calculateCost() {
        int entryCreditAmount = 1000;
        long entryCreditRate = entryCreditRateResponse.getResult().getRate();
        long fctCost = Math.round((entryCreditAmount * entryCreditRate) + .49);
        return fctCost;
    }
}

