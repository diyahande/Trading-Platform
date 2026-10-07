import { ApiProperty } from '@nestjs/swagger';
import { IsIn, IsInt, IsISO8601, IsNumber, IsString, IsUUID, Matches, MaxLength, Min } from 'class-validator';

export class OrderPlacedNotificationRequestDto {
  @ApiProperty({ format: 'uuid', description: 'Order identifier without ORD- prefix.' })
  @IsUUID('4')
  orderId!: string;

  @ApiProperty({ format: 'uuid', description: 'Account reference used by auth_users.account_reference.' })
  @IsUUID('4')
  accountReference!: string;

  @ApiProperty({ maxLength: 20 })
  @IsString()
  @MaxLength(20)
  symbol!: string;

  @ApiProperty({ enum: ['BUY', 'SELL'] })
  @IsIn(['BUY', 'SELL'])
  side!: 'BUY' | 'SELL';

  @ApiProperty({ minimum: 1 })
  @IsInt()
  @Min(1)
  quantity!: number;

  @ApiProperty({ minimum: 0.01 })
  @IsNumber({ maxDecimalPlaces: 4 })
  @Min(0.01)
  limitPrice!: number;

  @ApiProperty({ maxLength: 3, example: 'USD' })
  @IsString()
  @Matches(/^[A-Z]{3}$/)
  currency!: string;

  @ApiProperty({ enum: ['NEW'] })
  @IsIn(['NEW'])
  status!: 'NEW';

  @ApiProperty({ format: 'date-time' })
  @IsISO8601()
  createdOn!: string;
}
