package com.example.bankingsystem.business.concretes;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import com.example.bankingsystem.business.abstracts.BankAccountService;
import com.example.bankingsystem.core.utilities.entities.AccountCreateErrorResponse;
import com.example.bankingsystem.core.utilities.entities.AccountCreateRequest;
import com.example.bankingsystem.core.utilities.entities.AccountCreateResponse;
import com.example.bankingsystem.core.utilities.entities.AccountCreateSuccessResponse;
import com.example.bankingsystem.core.utilities.entities.AccountDeleteResponse;
import com.example.bankingsystem.core.utilities.entities.AccountDepositMoneyRequest;
import com.example.bankingsystem.core.utilities.entities.AccountLogResponse;
import com.example.bankingsystem.core.utilities.entities.AccountMoneyTransferRequest;
import com.example.bankingsystem.core.utilities.entities.AccountMoneyTransferResponse;
import com.example.bankingsystem.core.utilities.entities.BankUser;
import com.example.bankingsystem.core.utilities.exchange.CollectApiCurrencyExchange;
import com.example.bankingsystem.dataAccess.abstracts.BankAccountDao;
import com.example.bankingsystem.entities.BankAccount;

@Component
@EnableCaching
public class BankAccountManager implements BankAccountService {

	private BankAccountDao bankAccountDao;
	private KafkaTemplate<String, String> producer;
	private CollectApiCurrencyExchange exchanger;
	private BankAccount bankAccount;
	private BankUser bankUser;

	@Autowired
	public BankAccountManager(BankAccountDao bankAccountDao, KafkaTemplate<String, String> producer,
			CollectApiCurrencyExchange exchanger) {
		this.bankAccountDao = bankAccountDao;
		this.producer = producer;
		this.exchanger = exchanger;
	}

	// Check E-Mail, TC Identity and Currency Type Format
	// If Formats are Valid, Create Account
	@Override
	public ResponseEntity<AccountCreateResponse> createAccount(AccountCreateRequest request) {
		if (!request.isEmailAddressValid(request.getEmail()))
			return new ResponseEntity<>(new AccountCreateErrorResponse("Invalid Mail Format!"), HttpStatus.BAD_REQUEST);
		else if (!request.isTCIdentityNumberValid(request.getTc()))
			return new ResponseEntity<>(new AccountCreateErrorResponse("Invalid TC Identity Format!"),
					HttpStatus.BAD_REQUEST);
		else if (!request.isTypeValid(request.getType()))
			return new ResponseEntity<>(new AccountCreateErrorResponse("Invalid Type!"), HttpStatus.BAD_REQUEST);
		else
			return new ResponseEntity<>(new AccountCreateSuccessResponse("Account is created succesfully!",
					bankAccountDao.saveBankAccount(bankAccountDao.createAccount(request))), HttpStatus.OK);
	}

	// Check User Using his/her ID
	// Check Account is Exist or Deleted
	// Control Cache value is Up-To-Date
	// Get Account Details
	@Override
	public ResponseEntity<BankAccount> getBankAccountDetails(String id, WebRequest webRequest) {
		try {
			bankUser = (BankUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (bankUser == null || !bankUser.getId().equals(bankAccountDao.getBankAccount(id).getUser_id()))
				return new ResponseEntity<>(null, HttpStatus.FORBIDDEN);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
		}

		if (bankAccountDao.getBankAccount(id) == null)
			return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);

		else {
			if (webRequest.checkNotModified(bankAccountDao.getLastModified(id)))
				return null;

			else
				return ResponseEntity.ok().lastModified(bankAccountDao.getLastModified(id))
						.body(bankAccountDao.getBankAccount(id));
		}
	}

	// Check User Using his/her ID
	// Check Account is Exist or Deleted
	// Deposit Money into Account and Update Account
	// Send Log to Kafka Producer
	// Get Updated Account Details
	@Override
	public ResponseEntity<BankAccount> depositMoney2BankAccount(String id, AccountDepositMoneyRequest depositedMoney) {
		try {
			bankUser = (BankUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (bankUser == null || !bankUser.getId().equals(bankAccountDao.getBankAccount(id).getUser_id()))
				return new ResponseEntity<>(null, HttpStatus.FORBIDDEN);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
		}

		if (bankAccountDao.getBankAccount(id) == null)
			return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
		else {
			bankAccount = bankAccountDao.getBankAccount(bankAccountDao.updateBalance(bankAccountDao
					.depositMoney2BankAccount(bankAccountDao.getBankAccount(id), depositedMoney.getAmount())));
			producer.send("logs", id + " deposit amount: " + depositedMoney.getAmount()
					+ bankAccountDao.getBankAccount(id).getType());
			return ResponseEntity.ok().lastModified(bankAccountDao.getLastModified(id)).body(bankAccount);
		}
	}

	// Check User Using his/her ID
	// Check Bank Accounts Exist or Deleted
	// Get Account Types for Exchange Operations
	// Check Withdrawed Balance
	// Do Deposit and Withdraw Operations
	// Update Accounts
	// Send Log to Kafka Producer
	// Get Updated Account Details
	// Notes:
	// - For gold operations, directly exchange is not possible,
	// so first, get gold price in TL and exchange on TL currency
	// - Conversion of values of the same type gives an error
	// (response is null), so in this case, do directly transfer operations
	@Override
	public ResponseEntity<AccountMoneyTransferResponse> moneyTransferFromBankAccount(String id,
			AccountMoneyTransferRequest request) {
		try {
			bankUser = (BankUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (bankUser == null || !bankUser.getId().equals(bankAccountDao.getBankAccount(id).getUser_id()))
				return new ResponseEntity<>(new AccountMoneyTransferResponse("Invalid Account Number"),
						HttpStatus.FORBIDDEN);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
		}

		if (bankAccountDao.getBankAccount(id) == null
				|| bankAccountDao.getBankAccount(request.getTransferredAccountNumber()) == null)
			return new ResponseEntity<>(new AccountMoneyTransferResponse("Account cannot be found!"),
					HttpStatus.NOT_FOUND);

		String withdrawedAccountType = bankAccountDao.getBankAccount(id).getType();
		String depositedAccountType = bankAccountDao.getBankAccount(request.getTransferredAccountNumber()).getType();

		if (bankAccountDao.isBalanceSufficientForWithdraw(bankAccountDao.getBankAccount(id), request.getAmount())) {
			int exchangedAmount, gramGoldPriceInTRY;
			if (!withdrawedAccountType.equals(depositedAccountType)) {

				if (depositedAccountType.equals("GAU")) {
					gramGoldPriceInTRY = exchanger.getGramGoldPriceInTRY("selling");
					if (!withdrawedAccountType.equals("TRY")) {
						exchangedAmount = exchanger.exchangeCurrencies(request.getAmount(), withdrawedAccountType,
								"TRY") / gramGoldPriceInTRY;
					} else
						exchangedAmount = request.getAmount() / gramGoldPriceInTRY;
				} else if (withdrawedAccountType.equals("GAU")) {
					gramGoldPriceInTRY = exchanger.getGramGoldPriceInTRY("buying");
					if (!depositedAccountType.equals("TRY"))
						exchangedAmount = exchanger.exchangeCurrencies(request.getAmount() * gramGoldPriceInTRY, "TRY",
								depositedAccountType);
					else
						exchangedAmount = request.getAmount() * gramGoldPriceInTRY;
				} else {
					exchangedAmount = exchanger.exchangeCurrencies(request.getAmount(), withdrawedAccountType,
							depositedAccountType);
				}
			} else {
				exchangedAmount = request.getAmount();
			}

			bankAccountDao.updateBalance(bankAccountDao.withdrawMoneyFromBankAccount(bankAccountDao.getBankAccount(id),
					request.getAmount()));
			bankAccountDao.updateBalance(bankAccountDao.depositMoney2BankAccount(
					bankAccountDao.getBankAccount(request.getTransferredAccountNumber()), exchangedAmount));

			producer.send("logs",
					id + " transfer amount: " + request.getAmount() + bankAccountDao.getBankAccount(id).getType()
							+ " transferred_account: " + request.getTransferredAccountNumber());

			return ResponseEntity.ok().lastModified(bankAccountDao.getLastModified(id))
					.body(new AccountMoneyTransferResponse("Transferred succesfully!"));
		} else {
			return new ResponseEntity<>(new AccountMoneyTransferResponse("Insufficient balance!"),
					HttpStatus.BAD_REQUEST);
		}
	}

	// Get Account Logs in Operations in List
	@Override
	public ResponseEntity<ArrayList<AccountLogResponse>> getAccountLogs(String id) {
		return new ResponseEntity<>(bankAccountDao.getAccountLogs(id), HttpStatus.OK);
	}

	// Check User Using his/her ID
	// Check Account is Exist or Deleted
	// Delete Account(Set isDeleted 'True')
	// Send Log to Kafka Producer
	@Override
	public ResponseEntity<AccountDeleteResponse> deleteBankAccount(String id) {
		try {
			bankUser = (BankUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (bankUser == null || !bankUser.getId().equals(bankAccountDao.getBankAccount(id).getUser_id()))
				return new ResponseEntity<>(new AccountDeleteResponse("Invalid Account Number"), HttpStatus.FORBIDDEN);
		} catch (Exception e) {
			// TODO: handle exception
			return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
		}

		if (bankAccountDao.deleteBankAccount(bankAccountDao.getBankAccount(id))) {
			producer.send("logs", id + " delete");
			return ResponseEntity.ok().lastModified(bankAccountDao.getLastModified(id))
					.body(new AccountDeleteResponse("Account is deleted succesfully!"));
		} else
			return new ResponseEntity<>(new AccountDeleteResponse("Account cannot be found!"), HttpStatus.NOT_FOUND);
	}

}
