/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.wallet.account;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.novacrypto.bip39.MnemonicGenerator;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.aion.base.util.TypeConverter;
import org.aion.crypto.ECKey;
import org.aion.gui.events.EventPublisher;
import org.aion.gui.model.BalanceRetriever;
import org.aion.gui.util.AionConstants;
import org.aion.mcf.account.KeystoreFormat;
import org.aion.wallet.connector.dto.BlockDTO;
import org.aion.wallet.connector.dto.SendTransactionDTO;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.crypto.MasterKey;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.dto.TransactionDTO;
import org.aion.wallet.exception.ValidationException;
import org.aion.wallet.storage.WalletStorage;
import org.aion.wallet.util.CryptoUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AccountManagerTest {
    private BalanceRetriever balanceProvider;
    private Supplier<String> currencySupplier;
    private ConsoleManager consoleManager;
    private WalletStorage walletStorage;
    private Map<String, AccountDTO> addressToAccount;
    private AccountManager.MnemonicGeneratorWrapper mnemonicGenerator;
    private AccountManager.KeystoreWrapper keystoreWrapper;
    private EventPublisher eventPublisher;

    private static final int NOT_DERIVED_SENTINEL = -1;

    @Before
    public void before() {
        balanceProvider = mock(BalanceRetriever.class);
        currencySupplier = () -> AionConstants.CCY;
        consoleManager = mock(ConsoleManager.class);
        walletStorage = mock(WalletStorage.class);
        addressToAccount = new HashMap<>();
        mnemonicGenerator = mock(AccountManager.MnemonicGeneratorWrapper.class);
        keystoreWrapper = mock(AccountManager.KeystoreWrapper.class);
        eventPublisher = mock(EventPublisher.class);
    }

    @Test
    public void testCtor() {
        String[] accountList =
                new String[] {
                    "0xc0ffee1111111111111111111111111111111111111111111111111111111111",
                    "0xdecaf22222222222222222222222222222222222222222222222222222222222",
                };
        when(keystoreWrapper.list()).thenReturn(accountList);
        when(walletStorage.getAccountName(accountList[0])).thenReturn("Coffee!");
        when(walletStorage.getAccountName(accountList[1])).thenReturn("Decaf!");

        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);
        assertThat(unit.getAccounts().size(), is(2));
        assertThat(
                unit.getAccounts()
                        .stream()
                        .map(acc -> acc.getPublicAddress())
                        .collect(Collectors.toList()),
                is(Arrays.asList(accountList)));
        assertThat(unit.getAccounts().get(0).getName(), is("Coffee!"));
        assertThat(unit.getAccounts().get(1).getName(), is("Decaf!"));
    }

    @Test
    public void testCreateMasterAccount() throws Exception {
        String[] accountList = new String[0];
        when(keystoreWrapper.list()).thenReturn(accountList);

        String mnemonic = "a mnemonic is also called a seed phrase";
        doAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocationOnMock)
                                    throws Exception {
                                ((MnemonicGenerator.Target) invocationOnMock.getArguments()[1])
                                        .append(mnemonic);
                                return null;
                            }
                        })
                .when(mnemonicGenerator)
                .createMnemonic(any(byte[].class), any(MnemonicGenerator.Target.class));

        int derivIndex = 0;
        when(walletStorage.getMasterAccountDerivations())
                .thenReturn(derivIndex); // since we're creating a master account

        String name = "name";
        String password = "pass";
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);

        String result = unit.createMasterAccount(password, name);

        // verify return value
        assertThat(result, is(mnemonic));
        // verify master ("root") key creation
        assertThat(
                unit.getRoot().getEcKey().getPubKey(),
                is(CryptoUtils.generateSha512HashedBip39ECKey(mnemonic).getPubKey()));
        assertThat(
                unit.getRoot().getEcKey().getPrivKeyBytes(),
                is(CryptoUtils.generateSha512HashedBip39ECKey(mnemonic).getPrivKeyBytes()));
        // verify creation of new accountDTO and its derived key
        ECKey derivedKey = unit.getRoot().deriveHardened(new int[] {44, 425, 0, 0, derivIndex});
        assertThat(unit.getAccounts().size(), is(1));
        assertThat(unit.getAccounts().get(0).getName(), is(name));
        assertThat(unit.getAccounts().get(0).getDerivationIndex(), is(0));
        assertThat(unit.getAccounts().get(0).getPrivateKey(), is(derivedKey.getPrivKeyBytes()));
        assertThat(
                unit.getAccounts().get(0).getPublicAddress(),
                is(TypeConverter.toJsonHex(derivedKey.computeAddress(derivedKey.getPubKey()))));
        // verify master account is stored
        verify(walletStorage).setMasterAccountMnemonic(mnemonic, password);
        verify(walletStorage).incrementMasterAccountDerivations();
        verify(walletStorage).setAccountName(unit.getAccounts().get(0).getPublicAddress(), name);
        verify(eventPublisher).fireAccountAdded(unit.getAccounts().get(0));
    }

    @Test
    public void testImportMasterAccount() throws Exception {
        String[] accountList = new String[0];
        when(keystoreWrapper.list()).thenReturn(accountList);
        String mnemonic = "my cool mnemonic";
        String password = "pw";

        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);
        unit.importMasterAccount(mnemonic, password);

        // verify master ("root") key creation
        assertThat(
                unit.getRoot().getEcKey().getPubKey(),
                is(CryptoUtils.generateSha512HashedBip39ECKey(mnemonic).getPubKey()));
        assertThat(
                unit.getRoot().getEcKey().getPrivKeyBytes(),
                is(CryptoUtils.generateSha512HashedBip39ECKey(mnemonic).getPrivKeyBytes()));
        // verify creation of new accountDTO and its derived key
        int derivIndex = 0;
        ECKey derivedKey = unit.getRoot().deriveHardened(new int[] {44, 425, 0, 0, derivIndex});
        assertThat(unit.getAccounts().size(), is(1));
        assertThat(unit.getAccounts().get(0).getName(), is(nullValue()));
        assertThat(unit.getAccounts().get(0).getDerivationIndex(), is(0));
        assertThat(unit.getAccounts().get(0).getPrivateKey(), is(derivedKey.getPrivKeyBytes()));
        assertThat(
                unit.getAccounts().get(0).getPublicAddress(),
                is(TypeConverter.toJsonHex(derivedKey.computeAddress(derivedKey.getPubKey()))));
        assertThat(unit.getAccounts().get(0).getDerivationIndex(), is(0));
        // verify master account is stored
        verify(walletStorage).setMasterAccountMnemonic(mnemonic, password);
        verify(walletStorage).incrementMasterAccountDerivations();
        verify(eventPublisher).fireAccountAdded(unit.getAccounts().get(0));
    }

    @Test
    public void testUnlockMasterAccountWhenMasterAccountDoesNotExist() throws Exception {
        String[] accountList = new String[0];
        when(keystoreWrapper.list()).thenReturn(accountList);
        when(walletStorage.hasMasterAccount()).thenReturn(false);
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);

        unit.unlockMasterAccount("anything");

        verify(keystoreWrapper).list();
        verify(walletStorage).hasMasterAccount();
        verifyNoMoreInteractions(
                balanceProvider, consoleManager, walletStorage, mnemonicGenerator, keystoreWrapper);
    }

    @Test
    public void testUnlockMasterAccountWhenMasterAccountExists() throws Exception {
        // calculate the addresses needed for mocking the Keystore part
        String mnemonic =
                "weasel prison stable lawn fade hunt imitate voyage front hat cattle conduct";
        MasterKey rootMasterKey =
                new MasterKey(CryptoUtils.generateSha512HashedBip39ECKey(mnemonic));
        ECKey derivedKey1 = rootMasterKey.deriveHardened(new int[] {44, 425, 0, 0, 0});
        String address1 =
                TypeConverter.toJsonHex(derivedKey1.computeAddress(derivedKey1.getPubKey()));
        ECKey derivedKey2 = rootMasterKey.deriveHardened(new int[] {44, 425, 0, 0, 1});
        String address2 =
                TypeConverter.toJsonHex(derivedKey2.computeAddress(derivedKey2.getPubKey()));
        String[] accountList = new String[] {address1};
        when(keystoreWrapper.list()).thenReturn(accountList);

        String password = "password";
        when(walletStorage.hasMasterAccount()).thenReturn(true);
        when(walletStorage.getMasterAccountMnemonic("password")).thenReturn(mnemonic);
        when(walletStorage.getMasterAccountDerivations()).thenReturn(2);

        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);
        unit.unlockMasterAccount(password);

        ArgumentCaptor<Set<String>> accountsRecoveredCapture = ArgumentCaptor.forClass(Set.class);
        verify(eventPublisher).fireAccountsRecovered(accountsRecoveredCapture.capture());
        Set<String> accountsRecovered = accountsRecoveredCapture.getValue();
        assertThat(accountsRecovered.size(), is(2));
        assertThat(accountsRecovered.contains(address1), is(true));
        assertThat(accountsRecovered.contains(address2), is(true));

        assertThat(unit.getAccount(address1).getPrivateKey(), is(derivedKey1.getPrivKeyBytes()));
        assertThat(unit.getAccount(address1).isImported(), is(true));
        assertThat(unit.getAccount(address2).getPrivateKey(), is(derivedKey2.getPrivKeyBytes()));
        assertThat(unit.getAccount(address2).isImported(), is(false));
    }

    @Test
    public void testCreateAccount() throws Exception {
        // calculate the addresses needed for mocking the Keystore part
        String mnemonic =
                "weasel prison stable lawn fade hunt imitate voyage front hat cattle conduct";
        MasterKey rootMasterKey =
                new MasterKey(CryptoUtils.generateSha512HashedBip39ECKey(mnemonic));
        ECKey derivedKey1 = rootMasterKey.deriveHardened(new int[] {44, 425, 0, 0, 0});
        String address1 =
                TypeConverter.toJsonHex(derivedKey1.computeAddress(derivedKey1.getPubKey()));
        String[] accountList = new String[] {address1};
        when(keystoreWrapper.list()).thenReturn(accountList);

        String password = "password";
        when(walletStorage.hasMasterAccount()).thenReturn(true);
        when(walletStorage.getMasterAccountMnemonic("password")).thenReturn(mnemonic);
        when(walletStorage.getMasterAccountDerivations()).thenReturn(1);

        // need to unlock before we can create
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);
        unit.unlockMasterAccount(password);

        int nAccountsBefore = unit.getAccounts().size();

        unit.createAccount();

        assertThat(unit.getAccounts().size(), is(nAccountsBefore + 1));
        ArgumentCaptor<AccountDTO> newAcctCapture = ArgumentCaptor.forClass(AccountDTO.class);
        verify(eventPublisher).fireAccountAdded(newAcctCapture.capture());
        assertThat(newAcctCapture.getValue().getDerivationIndex(), is(1));
        // don't bother verifying the private key/public key of the account because
        // happens on a code path already covered in testCreateMasterAccount
    }

    @Test(expected = ValidationException.class)
    public void testImportKeystoreWhenShouldNotKeepAccountAndAccountAlreadyExists()
            throws Exception {
        // need to create the keystore bytes so we can use it as test input
        String mnemonic =
                "weasel prison stable lawn fade hunt imitate voyage front hat cattle conduct";
        MasterKey rootMasterKey =
                new MasterKey(CryptoUtils.generateSha512HashedBip39ECKey(mnemonic));
        ECKey derivedKey1 = rootMasterKey.deriveHardened(new int[] {44, 425, 0, 0, 0});

        String password = "pw";
        byte[] keystoreBytes = new KeystoreFormat().toKeystore(derivedKey1, password);

        when(keystoreWrapper.list()).thenReturn(new String[0]);
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);

        String pubAddressString =
                TypeConverter.toJsonHex(derivedKey1.computeAddress(derivedKey1.getPubKey()));

        boolean shouldKeep = false;
        addressToAccount.put(pubAddressString, mock(AccountDTO.class));
        unit.importKeystore(keystoreBytes, password, shouldKeep);

        assertThat(unit.getAccounts().size(), is(1));
        assertThat(unit.getAccounts().get(0).getDerivationIndex(), is(NOT_DERIVED_SENTINEL));
        assertThat(unit.getAccounts().get(0).getPrivateKey(), is(derivedKey1.getPrivKeyBytes()));
        assertThat(unit.getAccounts().get(0).getPublicAddress(), is(pubAddressString));
    }

    @Test
    public void testImportKeystoreWhenShouldNotKeepAccount() throws Exception {
        // need to create the keystore bytes so we can use it as test input
        String mnemonic =
                "weasel prison stable lawn fade hunt imitate voyage front hat cattle conduct";
        MasterKey rootMasterKey =
                new MasterKey(CryptoUtils.generateSha512HashedBip39ECKey(mnemonic));
        ECKey derivedKey1 = rootMasterKey.deriveHardened(new int[] {44, 425, 0, 0, 0});

        String password = "pw";
        byte[] keystoreBytes = new KeystoreFormat().toKeystore(derivedKey1, password);

        when(keystoreWrapper.list()).thenReturn(new String[0]);
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);

        String pubAddressString =
                TypeConverter.toJsonHex(derivedKey1.computeAddress(derivedKey1.getPubKey()));

        boolean shouldKeep = false;
        unit.importKeystore(keystoreBytes, password, shouldKeep);

        assertThat(unit.getAccounts().size(), is(1));
        assertThat(unit.getAccounts().get(0).getDerivationIndex(), is(NOT_DERIVED_SENTINEL));
        assertThat(unit.getAccounts().get(0).getPrivateKey(), is(derivedKey1.getPrivKeyBytes()));
        assertThat(unit.getAccounts().get(0).getPublicAddress(), is(pubAddressString));
    }

    @Test
    public void testImportKeystoreWhenShouldKeepAccount() throws Exception {
        // need to create the keystore bytes so we can use it as test input
        String mnemonic =
                "weasel prison stable lawn fade hunt imitate voyage front hat cattle conduct";
        MasterKey rootMasterKey =
                new MasterKey(CryptoUtils.generateSha512HashedBip39ECKey(mnemonic));
        ECKey derivedKey1 = rootMasterKey.deriveHardened(new int[] {44, 425, 0, 0, 0});

        String password = "pw";
        byte[] keystoreBytes = new KeystoreFormat().toKeystore(derivedKey1, password);

        when(keystoreWrapper.list()).thenReturn(new String[0]);
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);

        String pubAddressString =
                TypeConverter.toJsonHex(derivedKey1.computeAddress(derivedKey1.getPubKey()));
        ArgumentCaptor<ECKey> keystoreCreateECKeyCapture = ArgumentCaptor.forClass(ECKey.class);
        when(keystoreWrapper.create(
                        eq(password),
                        keystoreCreateECKeyCapture
                                .capture() /* because ECKey doesn't have equals() */))
                .thenReturn(pubAddressString);

        boolean shouldKeep = true;
        unit.importKeystore(keystoreBytes, password, shouldKeep);

        assertThat(keystoreCreateECKeyCapture.getValue().getPubKey(), is(derivedKey1.getPubKey()));
        assertThat(unit.getAccounts().size(), is(1));
        assertThat(unit.getAccounts().get(0).getDerivationIndex(), is(NOT_DERIVED_SENTINEL));
        assertThat(unit.getAccounts().get(0).getPrivateKey(), is(derivedKey1.getPrivKeyBytes()));
        assertThat(unit.getAccounts().get(0).getPublicAddress(), is(pubAddressString));
    }

    @Test
    public void testImportPrivateKey() throws Exception {
        // set up an arbitrary private key to use as test input
        ECKey key =
                CryptoUtils.generateSha512HashedBip39ECKey(
                        "weasel prison stable lawn fade hunt imitate voyage front hat cattle conduct");
        byte[] privKey = key.getPrivKeyBytes();

        when(keystoreWrapper.list()).thenReturn(new String[0]);
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);

        String password = "myPassword";
        String pubAddressString = TypeConverter.toJsonHex(key.computeAddress(key.getPubKey()));
        ArgumentCaptor<ECKey> keystoreCreateECKeyCapture = ArgumentCaptor.forClass(ECKey.class);
        when(keystoreWrapper.create(
                        eq(password),
                        keystoreCreateECKeyCapture
                                .capture() /* because ECKey doesn't have equals() */))
                .thenReturn(pubAddressString);

        boolean shouldKeep = true;
        unit.importPrivateKey(privKey, password, shouldKeep);

        assertThat(keystoreCreateECKeyCapture.getValue().getPubKey(), is(key.getPubKey()));
        assertThat(unit.getAccounts().size(), is(1));
        assertThat(unit.getAccounts().get(0).getDerivationIndex(), is(NOT_DERIVED_SENTINEL));
        assertThat(unit.getAccounts().get(0).getPrivateKey(), is(key.getPrivKeyBytes()));
        assertThat(unit.getAccounts().get(0).getPublicAddress(), is(pubAddressString));
    }

    @Test
    public void testUnlockAccountWhenAccountIsExternal() throws Exception {
        // external implies the keystore content is already in addressToKeystoreContent map

        // first we need to import an accout so that it is in that map
        ECKey key =
                CryptoUtils.generateSha512HashedBip39ECKey(
                        "weasel prison stable lawn fade hunt imitate voyage front hat cattle conduct");
        byte[] privKey = key.getPrivKeyBytes();

        when(keystoreWrapper.list()).thenReturn(new String[0]);
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);

        String password = "myPassword";
        String pubAddressString = TypeConverter.toJsonHex(key.computeAddress(key.getPubKey()));
        ArgumentCaptor<ECKey> keystoreCreateECKeyCapture = ArgumentCaptor.forClass(ECKey.class);
        when(keystoreWrapper.create(
                        eq(password),
                        keystoreCreateECKeyCapture
                                .capture() /* because ECKey doesn't have equals() */))
                .thenReturn(pubAddressString);

        boolean shouldKeep = true;
        unit.importPrivateKey(privKey, password, shouldKeep);

        AccountDTO account = unit.getAccounts().get(0);
        account.setActive(false);

        unit.unlockAccount(account, password);
        assertThat(account.isActive(), is(true));
        assertThat(account.getPrivateKey(), is(privKey));
        verify(eventPublisher).fireAccountAdded(account);
    }

    @Test
    public void testUnlockAccountWhenAccountIsInternal() throws Exception {
        // internal implies the keystore content is not already in addressToKeystoreContent map
        when(keystoreWrapper.list()).thenReturn(new String[0]);
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);

        String pubAddress = "some address";
        AccountDTO account =
                new AccountDTO("anyName", pubAddress, "anyBalance", "anyCurrency", false, 0);

        String password = "password";
        ECKey key =
                CryptoUtils.generateSha512HashedBip39ECKey(
                        "weasel prison stable lawn fade hunt imitate voyage front hat cattle conduct");
        when(keystoreWrapper.getKey("0x" + pubAddress, password)).thenReturn(key);

        unit.unlockAccount(account, password);
        assertThat(account.isActive(), is(true));
        assertThat(account.getPrivateKey(), is(key.getPrivKeyBytes()));
        verify(eventPublisher).fireAccountChanged(account);
    }

    @Test(expected = ValidationException.class)
    public void testUnlockAccountWhenPasswordWrong() throws Exception {
        // internal implies the keystore content is not already in addressToKeystoreContent map
        when(keystoreWrapper.list()).thenReturn(new String[0]);
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);

        String pubAddress = "some address";
        AccountDTO account =
                new AccountDTO("anyName", pubAddress, "anyBalance", "anyCurrency", false, 0);

        String password = "password";
        when(keystoreWrapper.getKey(anyString(), anyString())).thenReturn(null);
        unit.unlockAccount(account, password);
    }

    @Test
    public void testLockAll() throws Exception {
        // import a master account so we have something to lock
        String[] accountList = new String[0];
        when(keystoreWrapper.list()).thenReturn(accountList);
        String mnemonic = "my cool mnemonic";
        String password = "pw";

        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);
        unit.importMasterAccount(mnemonic, password);

        AccountDTO account = unit.getAccounts().get(0);
        account.setActive(true);
        byte[] nonNullBytes = "something not null".getBytes();
        account.setPrivateKey(nonNullBytes);

        unit.lockAll();

        assertThat(account.isActive(), is(false));
        assertThat(account.getPrivateKey(), is(nullValue()));
        assertThat(unit.isMasterAccountUnlocked(), is(false));
        verify(eventPublisher).fireAccountLocked(account);
    }

    @Test
    public void testGetOldestSafeBlock() {
        String[] accountList = new String[0];
        when(keystoreWrapper.list()).thenReturn(accountList);
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);

        Set<String> addresses = new LinkedHashSet<>();
        addresses.add("a1");
        addresses.add("a2");
        addresses.add("a3");

        AccountDTO a1 = mock(AccountDTO.class);
        AccountDTO a2 = mock(AccountDTO.class);
        AccountDTO a3 = mock(AccountDTO.class);
        addressToAccount.put("a1", a1);
        addressToAccount.put("a2", a2);
        addressToAccount.put("a3", a3);

        long b1Number = 1337;
        long b2Number = 31337;
        BlockDTO b1 = mock(BlockDTO.class);
        BlockDTO b2 = mock(BlockDTO.class);
        when(a1.getLastSafeBlock()).thenReturn(b1);
        when(b1.getNumber()).thenReturn(b1Number);
        when(a2.getLastSafeBlock()).thenReturn(b2);
        when(b2.getNumber()).thenReturn(b2Number);
        when(a3.getLastSafeBlock()).thenReturn(null);

        AtomicBoolean nullFilterUsed = new AtomicBoolean(false);

        BlockDTO result =
                unit.getOldestSafeBlock(
                        addresses,
                        iter -> {
                            nullFilterUsed.set(true);
                            assertThat(iter.hasNext(), is(false));
                        });

        assertThat(result, is(b1));
        assertThat(nullFilterUsed.get(), is(true));
    }

    @Test
    public void testAddRemoveGetTransactions() {
        String[] accountList = new String[0];
        when(keystoreWrapper.list()).thenReturn(accountList);
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);
        String address = "address";
        AccountDTO account =
                new AccountDTO("anyName", address, "anyBalance", "anyCurrency", false, 0);
        addressToAccount.put(address, account);

        assertThat(unit.getTransactions(address).isEmpty(), is(true));
        TransactionDTO tx1 = mock(TransactionDTO.class);
        TransactionDTO tx2 = mock(TransactionDTO.class);
        unit.addTransactions(address, Collections.singleton(tx1));
        assertThat(unit.getTransactions(address), is(Collections.singleton(tx1)));
        unit.addTransactions(address, Collections.singleton(tx2));
        assertThat(unit.getTransactions(address), is(new HashSet(Arrays.asList(tx1, tx2))));
        unit.removeTransactions(address, Arrays.asList(tx1, tx2));
        assertThat(unit.getTransactions(address).isEmpty(), is(true));
    }

    @Test
    public void testGetUpdateLastSafeBlock() {
        String[] accountList = new String[0];
        when(keystoreWrapper.list()).thenReturn(accountList);
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);
        String address = "address";
        AccountDTO account =
                new AccountDTO("anyName", address, "anyBalance", "anyCurrency", false, 0);
        addressToAccount.put(address, account);

        BlockDTO block1 = mock(BlockDTO.class);
        BlockDTO block2 = mock(BlockDTO.class);

        account.setLastSafeBlock(block1);
        assertThat(unit.getLastSafeBlock(address), is(block1));

        unit.updateLastSafeBlock(address, block2);
        assertThat(unit.getLastSafeBlock(address), is(block2));
    }

    @Test
    public void testGetAddresses() {
        String[] accountList =
                new String[] {
                    "0xc0ffee1111111111111111111111111111111111111111111111111111111111",
                    "0xdecaf22222222222222222222222222222222222222222222222222222222222",
                };
        when(keystoreWrapper.list()).thenReturn(accountList);
        when(walletStorage.getAccountName(accountList[0])).thenReturn("Coffee!");
        when(walletStorage.getAccountName(accountList[1])).thenReturn("Decaf!");
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);

        assertThat(unit.getAddresses().size(), is(2));
        assertThat(unit.getAddresses().containsAll(Arrays.asList(accountList)), is(true));
    }

    @Test
    public void testUpdateAccount() {
        String[] accountList =
                new String[] {"0xc0ffee1111111111111111111111111111111111111111111111111111111111"};
        when(keystoreWrapper.list()).thenReturn(accountList);
        when(walletStorage.getAccountName(accountList[0])).thenReturn("old name");
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);
        AccountDTO account =
                new AccountDTO("new name!", accountList[0], "anyBalance", "anyCurrency", false, 0);
        unit.updateAccount(account);
        verify(walletStorage).setAccountName(accountList[0], "new name!");
    }

    @Test
    public void testGetAddRemoveTimedOutTransactions() {
        String[] accountList = new String[0];
        when(keystoreWrapper.list()).thenReturn(accountList);
        AccountManager unit =
                new AccountManager(
                        balanceProvider,
                        currencySupplier,
                        consoleManager,
                        walletStorage,
                        addressToAccount,
                        mnemonicGenerator,
                        keystoreWrapper,
                        eventPublisher);
        String address = "address";
        AccountDTO account =
                new AccountDTO("anyName", address, "anyBalance", "anyCurrency", false, 0);
        addressToAccount.put(address, account);

        assertThat(unit.getTimedOutTransactions(address).isEmpty(), is(true));
        SendTransactionDTO tx1 = mock(SendTransactionDTO.class);
        when(tx1.getFrom()).thenReturn(address);
        SendTransactionDTO tx2 = mock(SendTransactionDTO.class);
        when(tx2.getFrom()).thenReturn(address);

        unit.addTimedOutTransaction(tx1);
        assertThat(unit.getTimedOutTransactions(address).size(), is(1));
        assertThat(unit.getTimedOutTransactions(address).get(0), is(tx1));

        unit.addTimedOutTransaction(tx2);
        assertThat(unit.getTimedOutTransactions(address).size(), is(2));
        assertThat(unit.getTimedOutTransactions(address).get(0), is(tx1));
        assertThat(unit.getTimedOutTransactions(address).get(1), is(tx2));

        unit.removeTimedOutTransaction(tx1);
        assertThat(unit.getTimedOutTransactions(address).size(), is(1));
        assertThat(unit.getTimedOutTransactions(address).get(0), is(tx2));

        unit.removeTimedOutTransaction(tx2);
        assertThat(unit.getTimedOutTransactions(address).isEmpty(), is(true));
    }
}
