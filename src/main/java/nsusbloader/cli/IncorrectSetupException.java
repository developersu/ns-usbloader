package nsusbloader.cli;

public class IncorrectSetupException extends Exception {
    public IncorrectSetupException(String errorMessage){
        super(errorMessage);
    }
}
