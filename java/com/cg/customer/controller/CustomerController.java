package com.cg.customer.controller;

import java.sql.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cg.customer.dto.CreateCustomerRequest;
import com.cg.customer.dto.CustomerDetails;
import com.cg.customer.dto.LoanDetails;
import com.cg.customer.dto.UserDetailsDto;
import com.cg.customer.entity.Customer;
import com.cg.customer.entity.Emi;
import com.cg.customer.entity.Loan;
import com.cg.customer.entity.LoanTracker;
import com.cg.customer.entity.UserDetails;
import com.cg.customer.exception.CustomerApprovedException;
import com.cg.customer.exception.CustomerLandOrFinanceException;
import com.cg.customer.exception.LoanNotApproved;
import com.cg.customer.exception.LoginRequiredException;
import com.cg.customer.exception.NotAccessibleException;
import com.cg.customer.service.ICustomerLoanService;
import com.cg.customer.service.ICustomerRegister;
import com.cg.customer.service.ICustomerService;
import com.cg.customer.util.CustomerUtil;
import com.cg.customer.util.LoanUtil;
@RestController
@RequestMapping("/customer")
@Validated
public class CustomerController {
	
	@Autowired
	private ICustomerRegister cRegister;

	@Autowired
	private ICustomerService customerService;
	
	@Autowired
	private ICustomerLoanService customerLoanService;
	
	@Autowired
	private CustomerUtil customerUtil;

	@Autowired
	private LoanUtil loanUtil;
	
	private Logger logger = Logger.getLogger(CustomerController.class);
	
	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping("/add")
	public CustomerDetails addPersonalDetails(@RequestBody @Valid CreateCustomerRequest requestData,
			HttpServletRequest request) {
		HttpSession session = request.getSession();
		if (session.getAttribute("username") == null) {
			throw new LoginRequiredException("Please login first");
		}
		if (!session.getAttribute("role").equals("User")) {
			throw new NotAccessibleException("Cannot be accessed by : " + session.getAttribute("role"));
		}
		if (session.getAttribute("custid") != null) {
			throw new NotAccessibleException("You have already entered the personal details");
		}
		Customer customer = new Customer(requestData.getName(), requestData.getMobileNumber(), requestData.getEmailId(),
				requestData.getDate(), requestData.getGender(), requestData.getNationality(),
				requestData.getAadharNumber(), requestData.getPan());
		customer = customerService.register(customer);
		Object uName = session.getAttribute("username");
		String name = (String) uName;
		UserDetails udetails = cRegister.findByName(name);
		System.out.println(udetails);
		if (udetails == null) {
			return null;
		}
		udetails.setCustid(customer.getId());
		udetails = cRegister.register1(udetails);
		CustomerDetails details = customerUtil.toDetails(customer);
		return details;
	}

	// Only Admin
	@GetMapping("/allcustomers")
	public List<CustomerDetails> findall(HttpServletRequest request) {
		HttpSession session = request.getSession();
		if (session.getAttribute("username") == null) {
			throw new LoginRequiredException("Please login first");
		}
		if (!session.getAttribute("role").equals("Admin")) {
			throw new NotAccessibleException("Cannot be accessed by : " + session.getAttribute("role"));
		}
		List<Customer> customers = customerService.findAll();
		List<CustomerDetails> details = customerUtil.toDetails(customers);
		return details;
	}

	// Only Admin
	@GetMapping("/by/id/{id}")
	public CustomerDetails findCustomerById(@PathVariable("id") int id, HttpServletRequest request) {
		HttpSession session = request.getSession();
		if (session.getAttribute("username") == null) {
			throw new LoginRequiredException("Please login first");
		}
		if (!session.getAttribute("role").equals("Admin")) {
			throw new NotAccessibleException("Cannot be accessed by : " + session.getAttribute("role"));
		}
		Customer customer = customerService.findById(id);
		CustomerDetails details = customerUtil.toDetails(customer);
		return details;
	}

	// Only User
	@GetMapping("/loan/Apply/{date}/{applyamount}/{custid}")
	public LoanDetails addLoan(@PathVariable("date") Date date, @PathVariable("applyamount") float amount,
			HttpServletRequest request) {
		HttpSession session = request.getSession();
		if (session.getAttribute("username") == null) {
			throw new LoginRequiredException("Please login first");
		}
		if (!session.getAttribute("role").equals("User")) {
			throw new NotAccessibleException("Cannot be accessed by : " + session.getAttribute("role"));
		}
		if (session.getAttribute("custid") == null) {
			throw new NotAccessibleException("Update your personal details in addPersonalDetails().......");
		}
		Object custid = session.getAttribute("custid");
		int cid = (int) custid;
		logger.info("Applying loan for customer with id : " + cid);
		Loan loan = new Loan(date, amount, cid, "Not Approved");
		loan = customerLoanService.register(loan);
		logger.info("Loan applied with id : " + loan.getApplicationid() + " for customer with id : " + cid);
		LoanDetails details = loanUtil.toDetails(loan);
		return details;
	}

	// Only FVO and Admin
	@GetMapping("/loan/updateFinanceDocuments/{customerid}/{applicationid}/{finance}")
	public LoanDetails updateLoanFinance(@PathVariable("customerid") int id, @PathVariable("applicationid") int appid,
			@PathVariable("finance") boolean financeupdate, HttpServletRequest request) {
		HttpSession session = request.getSession();
		if (session.getAttribute("username") == null) {
			throw new LoginRequiredException("Please login first");
		}
		if (session.getAttribute("role").equals("User")) {
			throw new NotAccessibleException("Cannot be accessed by : " + session.getAttribute("role"));
		}
		logger.info("Updating Finance Documents for customer with id : " + id + " for application id : " + appid);
		Customer customer = customerService.findById(id);
		Loan loan = customerLoanService.findByCustId(id, appid);
		if (loan.getStatus().equals("Approved") || loan.getStatus().equals("Rejected")) {
			logger.error("Tried to update agian.......");
			throw new CustomerApprovedException("Already Updated....... Can't update again");
		} else {
			loan.setFinanceverify(financeupdate);
			loan = customerLoanService.register(loan);
		}
		logger.info("Updated Finance Documents for customer with id : " + id + " for application id : " + appid);
		LoanDetails details = loanUtil.toDetails(loan);
		return details;
	}

	// Only LVO and Admin
	@GetMapping("/loan/updateLandDocuments/{customerid}/{applicationid}/{landdocuments}")
	public LoanDetails updateLoanLandDocuments(@PathVariable("customerid") int id,
			@PathVariable("applicationid") int appid, @PathVariable("landdocuments") boolean landdocumentsupdate,
			HttpServletRequest request) {
		HttpSession session = request.getSession();
		if (session.getAttribute("username") == null) {
			throw new LoginRequiredException("Please login first");
		}
		if (session.getAttribute("role").equals("User")) {
			throw new NotAccessibleException("Cannot be accessed by : " + session.getAttribute("role"));
		}
		logger.info("Updating Land Documents for customer with id : " + id + " for application id : " + appid);
		Customer customer = customerService.findById(id);
		Loan loan = customerLoanService.findByCustId(id, appid);
		if (loan.getStatus().equals("Approved") || loan.getStatus().equals("Rejected")) {
			logger.error("Tried to update agian.......");
			throw new CustomerApprovedException("Already Updated....... Can't update again");
		} else {
			loan.setLandverify(landdocumentsupdate);
			loan = customerLoanService.register(loan);
		}
		logger.info("Updated Finance Documents for customer with id : " + id + " for application id : " + appid);
		LoanDetails details = loanUtil.toDetails(loan);
		return details;
	}

	// Only Admin
	@GetMapping("/loan/updateAdminApprove/{customerid}/{applicationid}/{approvedamount}/{adminapprove}/{rejectdata}")
	public LoanDetails updateAdminApproval(@PathVariable("customerid") int id, @PathVariable("applicationid") int appid,
			@PathVariable("approvedamount") float approveamount,
			@PathVariable("adminapprove") boolean adminapprovalupdate, @PathVariable("rejectdata") String reject,
			HttpServletRequest request) {
		HttpSession session = request.getSession();
		if (session.getAttribute("username") == null) {
			throw new LoginRequiredException("Please login first");
		}
		if (!session.getAttribute("role").equals("Admin")) {
			throw new NotAccessibleException("Cannot be accessed by : " + session.getAttribute("role"));
		}
		logger.info("Admin approval in process for customer with id : " + id + " for application id : " + appid);
		Customer customer = customerService.findById(id);
		Loan loan = customerLoanService.findByCustId(id, appid);
		if (!loan.isLandverify() || !loan.isFinanceverify()) {
			logger.error("Land and Finance Document Approval required for customer with id : " + id
					+ " for application id : " + appid);
			throw new CustomerLandOrFinanceException("Land Documents or Finance Document Approval Required");
		} else if (loan.getStatus().equals("Approved") || loan.getStatus().equals("Rejected")) {
			logger.error("Tried to update agian.......");
			throw new CustomerApprovedException("Already Updated....... Can't update again");
		} else {
			if (reject.equals("r")) {
				loan.setAdminapprove(false);
				loan.setApproveamount(approveamount);
				loan.setStatus("Rejected");
				logger.info("Loan processing done for customer with id : " + id + " for application id : " + appid
						+ " ........ Rejected");
				loan = customerLoanService.register(loan);
			} else {
				loan.setAdminapprove(adminapprovalupdate);
				loan.setApproveamount(approveamount);
				loan.setStatus("Approved");
				logger.info("Loan processing done for customer with id : " + id + " for application id : " + appid
						+ " ......... Approved");
				loan = customerLoanService.register(loan);
			}
		}
		logger.info("Loan processing done for customer with id : " + id + " for application id : " + appid);
		LoanDetails details = loanUtil.toDetails(loan);
		return details;
	}

	// deleting loan
	// Only User
	@GetMapping("/loan/removeLoanByCustomerId/{appid}")
	public LoanDetails deleteLoan(@PathVariable("appid") int appid, HttpServletRequest request) {
		HttpSession session = request.getSession();
		if (session.getAttribute("username") == null) {
			throw new LoginRequiredException("Please login first");
		}
		if (!session.getAttribute("role").equals("User")) {
			throw new NotAccessibleException("Cannot be accessed by : " + session.getAttribute("role"));
		}
		if (session.getAttribute("custid") == null) {
			throw new NotAccessibleException("Update your personal details in addPersonalDetails().......");
		}
		Object custid = session.getAttribute("custid");
		int cid = (int) custid;
		logger.info("Deleting loan for customer id : " + cid + " with application id : " + appid);
		Customer customer = customerService.findById(cid);
		Loan loan = customerLoanService.findByCustId(cid, appid);
		if (loan.getStatus().equals("Approved") || loan.getStatus().equals("Rejected")) {
			logger.error("Tried to delete approved/rejected loan.......");
			throw new CustomerApprovedException("Cannot delete approved/rejected loans");
		}
		LoanDetails details = loanUtil.toDetails(loan);
		customerLoanService.remove(loan.getApplicationid());
		return details;
	}

	// Only User
	@GetMapping("/loan/getAllLoansAppliedByCustomerId")
	public List<LoanDetails> getAllLoansByCustomerId(HttpServletRequest request) {
		HttpSession session = request.getSession();
		if (session.getAttribute("username") == null) {
			throw new LoginRequiredException("Please login first");
		}
		if (!session.getAttribute("role").equals("User")) {
			throw new NotAccessibleException("Cannot be accessed by : " + session.getAttribute("role"));
		}
		if (session.getAttribute("custid") == null) {
			throw new NotAccessibleException("Update your personal details in addPersonalDetails().......");
		}
		Object custid = session.getAttribute("custid");
		int cid = (int) custid;
		List<Loan> loans = customerLoanService.findByCustId(cid);
		List<LoanDetails> details = loanUtil.toDetails(loans);
		logger.info("Displayed all loans applied by customer id : " + cid);
		return details;
	}

	// Only User
	@GetMapping("/loan/loanTracker/{applicationid}")
	public LoanTracker getLoanDetails(@PathVariable("applicationid") int appid, HttpServletRequest request) {
		HttpSession session = request.getSession();
		if (session.getAttribute("username") == null) {
			throw new LoginRequiredException("Please login first");
		}
		if (!session.getAttribute("role").equals("User")) {
			throw new NotAccessibleException("Cannot be accessed by : " + session.getAttribute("role"));
		}
		if (session.getAttribute("custid") == null) {
			throw new NotAccessibleException("Update your personal details in addPersonalDetails().......");
		}
		Object custid = session.getAttribute("custid");
		int cid = (int) custid;
		logger.info("Loan Status Tracking for customer id : " + cid + " with application id + " + appid);
		Loan loan = customerLoanService.findByCustId(cid, appid);
		LoanTracker lt = new LoanTracker();

		lt = customerLoanService.loanTracker(loan);
		logger.info("Loan Status Displayed for customer id : " + cid + " with application id + " + appid);
		return lt;
	}

	// Only User
	@GetMapping("/emi/approvedEMI/{applicationid}/{rateOfInterest}/{timePeriod}")
	public Emi calculateApprovedEmi(@PathVariable("applicationid") int appid,
			@PathVariable("rateOfInterest") double rateOfInterest, @PathVariable("timePeriod") int timePeriod,
			HttpServletRequest request) {
		HttpSession session = request.getSession();
		if (session.getAttribute("username") == null) {
			throw new LoginRequiredException("Please login first");
		}
		if (!session.getAttribute("role").equals("User")) {
			throw new NotAccessibleException("Cannot be accessed by : " + session.getAttribute("role"));
		}
		if (session.getAttribute("custid") == null) {
			throw new NotAccessibleException("Update your personal details in addPersonalDetails().......");
		}
		Object custid = session.getAttribute("custid");
		int cid = (int) custid;
		logger.info("Checking EMI for customer id : " + cid + " with application id : " + appid);
		Loan loan = customerLoanService.findByCustId(cid, appid);
		Emi emi = null;
		if (loan.getStatus().equals("Approved")) {
			double loanAmount = loan.getApproveamount();
			double interest = (double) ((loanAmount * (rateOfInterest * 0.01)) / timePeriod);
			double emiAmount = ((loanAmount / timePeriod) + interest);
			double totalEmiAmount = interest * timePeriod;
			double totalAmount = emiAmount * timePeriod;
			emi = new Emi(loanAmount, rateOfInterest, timePeriod, interest, emiAmount, totalEmiAmount, totalAmount);
		} else {
			logger.error("Cannot display EMI for rejected loans.......");
			throw new LoanNotApproved("Loan is not approved to check EMI");
		}
		logger.info("EMI details displayed for customer id : " + cid + " with application id : " + appid);
		return emi;
	}

	@GetMapping("/emi/simpleEMI/{loanAmount}/{rateOfInterest}/{timePeriod}")
	public Emi calculateEmi(@PathVariable("loanAmount") float loanAmount,
			@PathVariable("rateOfInterest") double rateOfInterest, @PathVariable("timePeriod") int timePeriod) {
		logger.info("Using simple EMI calculator.........");
		double interest = (double) ((loanAmount * (rateOfInterest * 0.01)) / timePeriod);
		double emiAmount = ((loanAmount / timePeriod) + interest);
		double totalEmiAmount = interest * timePeriod;
		double totalAmount = emiAmount * timePeriod;
		Emi emi = new Emi(loanAmount, rateOfInterest, timePeriod, interest, emiAmount, totalEmiAmount, totalAmount);
		logger.info("EMI details displayed for entered values");
		return emi;
	}

	@ResponseStatus(HttpStatus.ACCEPTED)
	@PostMapping("/login")
	public String login(@RequestBody UserDetailsDto userDetails, HttpServletRequest request) {
		HttpSession session = request.getSession();
		UserDetails uDetails = cRegister.findByName(userDetails);
		session.setAttribute("username", uDetails.getUsername());
		session.setAttribute("role", uDetails.getUserRole());
		session.setAttribute("custid", uDetails.getCustid());
		return "Login Successful.......Welcome " + uDetails.getUsername() + " ->" + uDetails.getUserRole();
	}

	@ResponseStatus(HttpStatus.ACCEPTED)
	@PostMapping("/logout")
	public String logout(@RequestBody UserDetailsDto userDetails, HttpServletRequest request) {
		HttpSession session = request.getSession();
		if (userDetails.getUsername().equals(session.getAttribute("username"))) {
			session.invalidate();
			return "You have successfully logged out " + userDetails.getUsername();
		}
		return "Not logged off";
	}

	@ResponseStatus(HttpStatus.ACCEPTED)
	@PostMapping("/login/register")
	public String register(@RequestBody UserDetailsDto userDetails, HttpServletRequest request) {
		UserDetails uDetails = new UserDetails(userDetails.getUsername(), userDetails.getPassword(), "User");
		uDetails = cRegister.register(uDetails);
		return "Registration successful with Username : " + uDetails.getUsername() + " Role-> "
				+ uDetails.getUserRole();
	}
}