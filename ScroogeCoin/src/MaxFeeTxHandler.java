
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;



public class MaxFeeTxHandler {

	/**
	 * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
	 * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
	 * constructor.
	 */
	private UTXOPool pool = null;

	public MaxFeeTxHandler(UTXOPool utxoPool) {
		pool = new UTXOPool(utxoPool);
	}

	/**
	 * @return true if:
	 * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
	 * (2) the signatures on each input of {@code tx} are valid, 
	 * (3) no UTXO is claimed multiple times by {@code tx},
	 * (4) all of {@code tx}s output values are non-negative, and
	 * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
	 *     values; and false otherwise.
	 */
	public boolean isValidTx(Transaction tx) {
		ArrayList<Transaction.Input> inputs = tx.getInputs();
		ArrayList<Transaction.Output> outputs = tx.getOutputs();
		double outputSum = 0;

		for (Transaction.Output output : outputs) {
			if (output.value <0){
				return false;
			}
			outputSum+= output.value;
		}

		double inputSum = 0;
		int inputIndex = 0;
		Set<UTXO> claimedUTXO = new HashSet<UTXO>();
		for (Transaction.Input input : inputs) {
			int index = input.outputIndex;
			byte[] prevHash = input.prevTxHash;
			UTXO utxo = new UTXO(prevHash, index);

			//(3) no UTXO is claimed multiple times by {@code tx},
			if (claimedUTXO.contains(utxo)){
				return false;
			}
			claimedUTXO.add(utxo);
			Transaction.Output output = pool.getTxOutput(utxo);

			//(1) all outputs claimed by {@code tx} are in the current UTXO pool
			if (output == null){
				return false;
			}
			inputSum += output.value;

			byte[] sig = input.signature;

			//(2) the signatures on each input of {@code tx} are valid, 
			if (!Crypto.verifySignature(output.address, tx.getRawDataToSign(inputIndex), sig)){
				return false;
			}
			inputIndex++;
		}

		//5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
		return inputSum>=outputSum;
	}

	/**
	 * Handles each epoch by receiving an unordered array of proposed transactions, checking each
	 * transaction for correctness, returning a mutually valid array of accepted transactions, and
	 * updating the current UTXO pool as appropriate.
	 */
	public Transaction[] handleTxs(Transaction[] possibleTxs) {

		Map<byte[], Transaction> validIdTx = new HashMap<byte[], Transaction>();
		Set<byte[]> invalidIdTx = new HashSet<byte[]>();
		
		populateValidAndInvalid(possibleTxs, validIdTx, invalidIdTx);
		
		Collection<Transaction> validTx = validIdTx.values();
		
		updatePool(validTx);
		
		//Build the result
		return buildResultHandling(Integer.MAX_VALUE-2, invalidIdTx, validTx);
	}

	private void updatePool(Collection<Transaction> validTx) {
		for (Transaction transaction : validTx) {
			for (Transaction.Input input : transaction.getInputs()) {
				UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
				this.pool.removeUTXO(utxo);
			}
			int pos = 0;
			for (Transaction.Output output : transaction.getOutputs()) {
				UTXO utxo = new UTXO(transaction.getHash(), pos++);
				this.pool.addUTXO(utxo, output);
			}
		}
	}

	private void populateValidAndInvalid(Transaction[] possibleTxs, Map<byte[], Transaction> validIdTx,
			Set<byte[]> invalidIdTx) {
		//All utxo and the transaction using them
		Map<UTXO, byte[]> utxoAndTxAssociated = new HashMap<UTXO, byte[]>();

		for (Transaction transaction : possibleTxs) {
			byte[] txKey = transaction.getHash();

			if (!isValidTx(transaction)){
				//THe transaction is not built correctly
				invalidIdTx.add(txKey);
			} 
			//Anyway we still need to look for double spending
			for (Transaction.Input input : transaction.getInputs()){
				UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
				if (utxoAndTxAssociated.containsKey(utxo)){
					invalidIdTx.add(txKey);
					//The original transaction was valid until now it's proven not
					invalidIdTx.add(utxoAndTxAssociated.get(utxo));
					//What costs less ? Adding every Tx which seems to be valid in validSet and removing 1st invalid double spending, 
					//or a 2nd loop on every transaction and add in validSet every Tx not in InvalidSet ?  
					validIdTx.remove(txKey);
				} else {
					//Not (yet) adouble spending
					utxoAndTxAssociated.put(utxo, txKey);
					if (!invalidIdTx.contains(txKey)){
						validIdTx.put(txKey,transaction);
					}
				}
			}
		}
	}

	private Transaction[] buildResultHandling(int txsLength, Set<byte[]> invalidIdTx,
			Collection<Transaction> validTx) {
		Transaction[] handledTxs = new Transaction[txsLength];
		int pos = 0;
		for (Iterator<Transaction> iterator = validTx.iterator(); iterator.hasNext();) {
			handledTxs[pos++] = iterator.next();
		}
		for (Iterator<byte[]> iterator = invalidIdTx.iterator(); iterator.hasNext();iterator.next()) {
			handledTxs[pos++] = new Transaction();
		}
		
		return handledTxs;
	}

}





























